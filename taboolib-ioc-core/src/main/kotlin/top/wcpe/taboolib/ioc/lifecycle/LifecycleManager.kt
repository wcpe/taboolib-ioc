package top.wcpe.taboolib.ioc.lifecycle

import taboolib.common.platform.function.debug
import taboolib.common.platform.function.warning
import top.wcpe.taboolib.ioc.aop.AopProxyFactory
import top.wcpe.taboolib.ioc.bean.BeanCreatedEvent
import top.wcpe.taboolib.ioc.bean.BeanDefinition
import top.wcpe.taboolib.ioc.bean.BeanDestroyedEvent
import top.wcpe.taboolib.ioc.bean.BeanPostProcessor
import top.wcpe.taboolib.ioc.bean.BeanRegistry
import top.wcpe.taboolib.ioc.bean.BeanScope
import top.wcpe.taboolib.ioc.bean.BeanScopes
import top.wcpe.taboolib.ioc.bean.ContainerInitializedEvent
import top.wcpe.taboolib.ioc.bean.ContainerShutdownEvent
import top.wcpe.taboolib.ioc.bean.InjectParameter
import top.wcpe.taboolib.ioc.cycle.CircularDependencyException
import top.wcpe.taboolib.ioc.cycle.CycleDetector
import top.wcpe.taboolib.ioc.cycle.CycleResolver
import top.wcpe.taboolib.ioc.inject.Injector
import java.util.ArrayDeque
import java.util.concurrent.ConcurrentHashMap

/**
 * 生命周期管理器。
 *
 * 单例 Bean 支持预初始化与循环依赖早期暴露；
 * prototype / 自定义 scope Bean 则在访问时按需创建。
 */
class LifecycleManager(
    private val registry: BeanRegistry,
    private val cycleResolver: CycleResolver,
    private val injector: Injector,
    private val cycleDetector: CycleDetector,
    private val scopeLookup: (String) -> BeanScope? = { null },
    private val aopProxyFactory: AopProxyFactory? = null,
    val eventBus: EventBus = EventBus()
) {

    private val initializationOrder = java.util.Collections.synchronizedList(mutableListOf<String>())
    private val initializedSingletons = ConcurrentHashMap.newKeySet<String>()
    private val singletonLocks = ConcurrentHashMap<String, Any>()
    private val creationStack = ThreadLocal.withInitial { ArrayDeque<String>() }
    private val beanPostProcessors = mutableListOf<BeanPostProcessor>()

    fun addBeanPostProcessor(processor: BeanPostProcessor) {
        beanPostProcessors.add(processor)
    }

    /**
     * 初始化容器
     */
    fun initialize() {
        val definitions = registry.getAll().toList()

        val validateStart = System.nanoTime()
        validateScopes(definitions)
        val validateMs = (System.nanoTime() - validateStart) / 1_000_000.0
        debug("[IoC] 作用域验证完成，耗时 ${"%.2f".format(validateMs)}ms")

        val cycleStart = System.nanoTime()
        logResolvableDependencyCycles(definitions.filter(BeanDefinition::isSingletonScope))
        val cycleMs = (System.nanoTime() - cycleStart) / 1_000_000.0
        debug("[IoC] 循环依赖检测完成，耗时 ${"%.2f".format(cycleMs)}ms")

        // 优先初始化切面 Bean，确保 AOP 代理在普通 Bean 创建前就绑定
        val eagerDefinitions = definitions.filter(BeanDefinition::isEagerSingleton)
        val (aspectDefs, normalDefs) = eagerDefinitions.partition { it.isAspect }
        val sortedNormalDefs = topologicalSort(normalDefs)
        val orderedDefs = aspectDefs + sortedNormalDefs
        debug("[IoC] 开始预初始化 ${orderedDefs.size} 个 eager singleton Bean（其中 ${aspectDefs.size} 个切面）")

        val eagerStart = System.nanoTime()
        orderedDefs.forEach { definition ->
            try {
                val beanStart = System.nanoTime()
                getOrCreateSingleton(definition)
                val beanMs = (System.nanoTime() - beanStart) / 1_000_000.0
                debug("[IoC] 预初始化 Bean: ${definition.name}，耗时 ${"%.2f".format(beanMs)}ms")
            } catch (e: Exception) {
                warning("[IoC] Bean 初始化失败: ${definition.name} - ${e.message}")
                throw e
            }
        }
        val eagerMs = (System.nanoTime() - eagerStart) / 1_000_000.0
        debug("[IoC] 预初始化完成，共 ${orderedDefs.size} 个 Bean，耗时 ${"%.2f".format(eagerMs)}ms")

        eventBus.publish(ContainerInitializedEvent(definitions.size))
    }

    fun getOrCreateSingleton(definition: BeanDefinition): Any {
        cycleResolver.getSingleton(definition.name)?.let { return it }

        val lock = singletonLocks.computeIfAbsent(definition.name) { Any() }
        synchronized(lock) {
            cycleResolver.getSingleton(definition.name)?.let { return it }
            return createBean(definition, cacheSingleton = true)
        }
    }

    fun createTransient(definition: BeanDefinition): Any {
        return createBean(definition, cacheSingleton = false)
    }

    /**
     * 执行所有已初始化 singleton Bean 的 @PostEnable 方法
     */
    fun invokePostEnable() {
        debug("[IoC] 开始执行 @PostEnable 回调，共 ${initializationOrder.size} 个 singleton")
        val start = System.nanoTime()

        for (name in initializationOrder) {
            val definition = registry.getByName(name) ?: continue
            if (definition.postEnableMethods.isEmpty()) continue
            val instance = cycleResolver.getSingleton(name) ?: continue
            for (postEnable in definition.postEnableMethods) {
                try {
                    val invokeStart = System.nanoTime()
                    postEnable.invoke(instance)
                    val invokeMs = (System.nanoTime() - invokeStart) / 1_000_000.0
                    debug("[IoC] @PostEnable 执行完成: $name.${postEnable.name}，耗时 ${"%.2f".format(invokeMs)}ms")
                } catch (e: Exception) {
                    warning("[IoC] @PostEnable 执行失败: $name.${postEnable.name} - ${e.message}")
                }
            }
        }

        val totalMs = (System.nanoTime() - start) / 1_000_000.0
        debug("[IoC] @PostEnable 回调执行完成，总耗时 ${"%.2f".format(totalMs)}ms")
    }

    /**
     * 重置内部状态，不触发生命周期回调
     */
    fun resetState() {
        initializationOrder.clear()
        initializedSingletons.clear()
        singletonLocks.clear()
        creationStack.remove()
        beanPostProcessors.clear()
    }

    /**
     * 关闭容器
     */
    fun shutdown() {
        debug("[IoC] 开始关闭容器，共 ${initializationOrder.size} 个 singleton 需要销毁")
        val start = System.nanoTime()

        for (name in initializationOrder.reversed()) {
            val definition = registry.getByName(name) ?: continue
            val instance = cycleResolver.getSingleton(name)
            try {
                for (preDestroy in definition.preDestroyMethods) {
                    val destroyStart = System.nanoTime()
                    preDestroy.invoke(instance)
                    val destroyMs = (System.nanoTime() - destroyStart) / 1_000_000.0
                    debug("[IoC] Bean 销毁完成: $name.${preDestroy.name}，耗时 ${"%.2f".format(destroyMs)}ms")
                }
                eventBus.publish(BeanDestroyedEvent(name, definition))
            } catch (e: Exception) {
                warning("[IoC] Bean 销毁失败: $name - ${e.message}")
            }
        }

        resetState()
        val totalMs = (System.nanoTime() - start) / 1_000_000.0
        debug("[IoC] 容器关闭完成，总耗时 ${"%.2f".format(totalMs)}ms")
        eventBus.publish(ContainerShutdownEvent())
    }

    private fun createBean(definition: BeanDefinition, cacheSingleton: Boolean): Any {
        val stack = creationStack.get()
        val cycleStart = stack.indexOf(definition.name)
        if (cycleStart >= 0) {
            val dependencyChain = stack.drop(cycleStart) + definition.name
            throw CircularDependencyException(definition.name, dependencyChain)
        }

        stack.addLast(definition.name)
        try {
            val createStart = System.nanoTime()

            val instantiateStart = System.nanoTime()
            val instance = injector.instantiate(definition)
            val instantiateMs = (System.nanoTime() - instantiateStart) / 1_000_000.0

            if (cacheSingleton) {
                cycleResolver.addSingleton(definition.name, instance)
            }

            val finalInstance: Any
            try {
                val populateStart = System.nanoTime()
                injector.populate(instance, definition)
                val populateMs = (System.nanoTime() - populateStart) / 1_000_000.0

                // BeanPostProcessor — before initialization
                var processedInstance = instance
                for (processor in beanPostProcessors) {
                    processedInstance = processor.postProcessBeforeInitialization(processedInstance, definition.name)
                }

                val postConstructStart = System.nanoTime()
                injector.invokePostConstruct(processedInstance, definition)
                val postConstructMs = (System.nanoTime() - postConstructStart) / 1_000_000.0

                // BeanPostProcessor — after initialization
                for (processor in beanPostProcessors) {
                    processedInstance = processor.postProcessAfterInitialization(processedInstance, definition.name)
                }

                // 如果 processedInstance 被替换了，更新 cycleResolver 中的缓存
                if (cacheSingleton && processedInstance !== instance) {
                    cycleResolver.addSingleton(definition.name, processedInstance)
                }

                // AOP 代理包装（切面 Bean 自身不被代理）
                finalInstance = if (!definition.isAspect && aopProxyFactory != null) {
                    val proxy = aopProxyFactory.wrapIfNecessary(processedInstance, definition.type)
                    if (proxy !== processedInstance) {
                        if (cacheSingleton) {
                            cycleResolver.addSingleton(definition.name, proxy)
                        }
                        debug("[IoC] Bean 已包装 AOP 代理: ${definition.name}")
                    }
                    proxy
                } else {
                    processedInstance
                }

                val totalMs = (System.nanoTime() - createStart) / 1_000_000.0
                debug(
                    "[IoC] Bean 创建完成: ${definition.name} " +
                        "[实例化=${"%.2f".format(instantiateMs)}ms, " +
                        "注入=${"%.2f".format(populateMs)}ms, " +
                        "PostConstruct=${"%.2f".format(postConstructMs)}ms, " +
                        "总计=${"%.2f".format(totalMs)}ms]"
                )
            } catch (e: Exception) {
                if (cacheSingleton) {
                    cycleResolver.removeSingleton(definition.name)
                    initializedSingletons.remove(definition.name)
                }
                throw e
            }

            if (cacheSingleton && initializedSingletons.add(definition.name)) {
                initializationOrder.add(definition.name)
            }
            eventBus.publish(BeanCreatedEvent(definition.name, finalInstance, definition))
            return finalInstance
        } finally {
            stack.removeLast()
        }
    }

    private fun validateScopes(definitions: List<BeanDefinition>) {
        definitions.forEach { definition ->
            val scope = BeanScopes.normalize(definition.scope)
            if (!BeanScopes.isStandard(scope) && !BeanScopes.isBuiltin(scope) && scopeLookup(scope) == null) {
                throw IllegalStateException("未注册的 Bean 作用域: ${definition.scope} (${definition.name})")
            }
        }
    }

    private fun logResolvableDependencyCycles(definitions: List<BeanDefinition>) {
        val definitionByName = definitions.associateBy { it.name }
        val cycles = cycleDetector.findCycles(
            nodes = definitions,
            nameOf = { it.name },
            dependenciesOf = { definition ->
                resolveDependencyDefinitions(definition.dependencies, definitionByName)
            }
        )

        cycles.forEach { cycle ->
            debug("[IoC] 检测到可由单例早期暴露处理的循环依赖: ${cycle.joinToString(" -> ")}")
        }
    }

    private fun resolveDependencyDefinition(
        type: Class<*>,
        name: String?,
        definitionByName: Map<String, BeanDefinition>
    ): BeanDefinition? {
        return if (name != null) {
            definitionByName[name]
        } else {
            registry.getPrimaryByType(type)?.takeIf { it.name in definitionByName }
        }
    }

    private fun resolveDependencyDefinitions(
        dependencies: List<InjectParameter>,
        definitionByName: Map<String, BeanDefinition>
    ): List<BeanDefinition> {
        return dependencies.mapNotNull { dependency ->
            resolveDependencyDefinition(dependency.type, dependency.nameQualifier, definitionByName)
        }
    }

    /**
     * 按 @DependsOn 声明进行拓扑排序。
     * 没有 @DependsOn 的 Bean 保持原有顺序。
     */
    private fun topologicalSort(definitions: List<BeanDefinition>): List<BeanDefinition> {
        if (definitions.none { it.dependsOn.isNotEmpty() }) return definitions

        val byName = definitions.associateBy { it.name }
        val visited = mutableSetOf<String>()
        val result = mutableListOf<BeanDefinition>()

        fun visit(def: BeanDefinition) {
            if (def.name in visited) return
            visited.add(def.name)
            for (depName in def.dependsOn) {
                val depDef = byName[depName]
                if (depDef != null) {
                    visit(depDef)
                }
            }
            result.add(def)
        }

        for (def in definitions) {
            visit(def)
        }
        return result
    }
}

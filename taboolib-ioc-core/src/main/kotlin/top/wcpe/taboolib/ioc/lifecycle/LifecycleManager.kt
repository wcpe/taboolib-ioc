package top.wcpe.taboolib.ioc.lifecycle

import taboolib.common.platform.function.debug
import taboolib.common.platform.function.warning
import top.wcpe.taboolib.ioc.annotation.PostEnable
import top.wcpe.taboolib.ioc.annotation.PreDestroy
import top.wcpe.taboolib.ioc.aop.AopProxyFactory
import top.wcpe.taboolib.ioc.bean.BeanCreatedEvent
import top.wcpe.taboolib.ioc.bean.BeanDefinition
import top.wcpe.taboolib.ioc.bean.BeanDestroyedEvent
import top.wcpe.taboolib.ioc.aop.WrapWithPostProcessor
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

    /**
     * 全局单例创建锁（Spring DefaultSingletonBeanRegistry 同款策略）：
     * 单一锁 + [singletonsInCreation] 集合，彻底消除「每 Bean 一把锁」在
     * 循环依赖 + 多线程场景下的锁顺序死锁（线程 T1 持 lock(A) 等 lock(B)，
     * 线程 T2 持 lock(B) 等 lock(A) → 主线程卡死 = 服务器瘫痪）。
     *
     * 启动期本就单线程；运行期只有 @Lazy / prototype 依赖的首创会进到这里，
     * 串行化代价可忽略。注意：@PostConstruct 内不要做长时间阻塞。
     */
    private val singletonCreationLock = Any()

    /** 仅在 [singletonCreationLock] 保护下访问：当前创建中的单例名（有序，用于输出依赖链） */
    private val singletonsInCreation = LinkedHashSet<String>()

    private val creationStack = ThreadLocal.withInitial { ArrayDeque<String>() }
    /**
     * 后处理器链。默认已包含**内置**的 [WrapWithPostProcessor]（`@WrapWith` 装饰器支持），
     * 它由容器自身维护 —— 这样扫描路径（[BeanContainer]）与测试路径（`IocTestContext`）
     * 语义天然一致，无需各自接线。
     */
    private val beanPostProcessors = mutableListOf<BeanPostProcessor>(WrapWithPostProcessor)

    fun addBeanPostProcessor(processor: BeanPostProcessor) {
        beanPostProcessors.add(processor)
    }

    fun getBeanPostProcessors(): List<BeanPostProcessor> = beanPostProcessors.toList()

    /**
     * 记录 Bean 的初始化顺序（用于手动注册的 Bean）
     */
    fun recordInitialization(name: String) {
        if (initializedSingletons.add(name)) {
            initializationOrder.add(name)
        }
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
        logResolvableDependencyCycles(definitions)
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

        // 阶段 1（锁内，仅做无用户代码的快速操作）：
        //   double-check → 环检测登记 → 实例化 → 早期暴露占位 → 登记 creationStack。
        // 锁内**不**执行 populate / @PostConstruct / AOP，避免 @PostConstruct 阻塞
        // 串行化所有懒加载 / prototype 解析（A-P1-01）。实例化本身通常很快
        // （复杂构造逻辑应放在 @PostConstruct，而非构造函数）。
        val instance: Any
        synchronized(singletonCreationLock) {
            cycleResolver.getSingleton(definition.name)?.let { return it }
            if (!singletonsInCreation.add(definition.name)) {
                // 同一线程在创建 A 的过程中再次请求创建 A → 构造器循环依赖
                // （A 尚未实例化完成、无早期暴露可用），直接抛出完整依赖链
                throw CircularDependencyException(
                    definition.name,
                    singletonsInCreation.toList() + definition.name
                )
            }
            try {
                instance = injector.instantiate(definition)
                // 早期暴露：使环内其他 Bean 的依赖解析可以命中（同 Spring early singleton）
                cycleResolver.addSingleton(definition.name, instance)
                creationStack.get().addLast(definition.name)
            } catch (e: Throwable) {
                synchronized(singletonCreationLock) { singletonsInCreation.remove(definition.name) }
                throw e
            }
        }

        // 阶段 2（锁外）：注入 → BPP → @PostConstruct → BPP → AOP。
        try {
            val finalInstance = finalizeSingleton(definition, instance)
            if (initializedSingletons.add(definition.name)) {
                initializationOrder.add(definition.name)
            }
            eventBus.publish(BeanCreatedEvent(definition.name, finalInstance, definition))
            return finalInstance
        } catch (e: Throwable) {
            cycleResolver.removeSingleton(definition.name)
            initializedSingletons.remove(definition.name)
            throw e
        } finally {
            creationStack.get().removeLast()
            synchronized(singletonCreationLock) { singletonsInCreation.remove(definition.name) }
        }
    }

    /**
     * 完成单例的注入与初始化阶段（锁外执行）：
     * `populate → BeanPostProcessor → @PostConstruct → BeanPostProcessor → AOP 包装`，
     * 并更新 `cycleResolver` 中的最终实例。
     */
    private fun finalizeSingleton(definition: BeanDefinition, instance: Any): Any {
        val createStart = System.nanoTime()

        val processedInstance = initializeInstance(instance, definition)

        // 如果 processedInstance 被替换了，更新 cycleResolver 中的缓存
        if (processedInstance !== instance) {
            cycleResolver.addSingleton(definition.name, processedInstance)
        }

        // AOP 代理包装（切面 Bean 自身不被代理）
        val finalInstance = if (!definition.isAspect && aopProxyFactory != null) {
            val proxy = aopProxyFactory.wrapIfNecessary(processedInstance, definition.type)
            if (proxy !== processedInstance) {
                cycleResolver.addSingleton(definition.name, proxy)
                debug("[IoC] Bean 已包装 AOP 代理: ${definition.name}")
            }
            proxy
        } else {
            processedInstance
        }

        val totalMs = (System.nanoTime() - createStart) / 1_000_000.0
        debug("[IoC] Bean 初始化完成: ${definition.name} [注入+初始化=${"%.2f".format(totalMs)}ms]")
        return finalInstance
    }

    fun createTransient(definition: BeanDefinition): Any {
        return createBean(definition, cacheSingleton = false)
    }

    /**
     * 为**已存在的实例**（手动 `registerBean`）执行完整生命周期并缓存：
     * 注入 → BeanPostProcessor → @PostConstruct → AOP 包装 → 缓存。
     *
     * 与 [createBean] 的区别：实例已经创建好，跳过构造函数实例化阶段；
     * 但**同样进入 `singletonsInCreation` 环检测门**，使手动 Bean 之间的
     * 循环依赖能被显式检测（A-P1-03）。
     *
     * 生命周期回调在锁外执行（收窄临界区，A-P1-01），
     * 锁内只做「环检测登记 + 实例早期暴露占位」。
     */
    fun registerExistingSingleton(definition: BeanDefinition, instance: Any): Any {
        // 已缓存（例如被更早的依赖解析命中并完成）→ 直接返回
        cycleResolver.getSingleton(definition.name)?.let { return it }

        synchronized(singletonCreationLock) {
            cycleResolver.getSingleton(definition.name)?.let { return it }
            if (!singletonsInCreation.add(definition.name)) {
                throw CircularDependencyException(
                    definition.name,
                    singletonsInCreation.toList() + definition.name
                )
            }
        }

        try {
            // 锁外：早期暴露原始实例占位，供环内其他手动 Bean 解析命中
            cycleResolver.addSingleton(definition.name, instance)

            val stack = creationStack.get()
            stack.addLast(definition.name)
            val finalInstance: Any
            try {
                val processedInstance = initializeInstance(instance, definition)
                // AOP 代理包装（切面 Bean 自身不被代理）
                finalInstance = if (!definition.isAspect && aopProxyFactory != null) {
                    val proxy = aopProxyFactory.wrapIfNecessary(processedInstance, definition.type)
                    if (proxy !== processedInstance) {
                        debug("[IoC] 手动注册的 Bean 已包装 AOP 代理: ${definition.name}")
                    }
                    proxy
                } else {
                    processedInstance
                }
            } catch (e: Exception) {
                cycleResolver.removeSingleton(definition.name)
                throw e
            } finally {
                stack.removeLast()
            }

            cycleResolver.addSingleton(definition.name, finalInstance)
            if (initializedSingletons.add(definition.name)) {
                initializationOrder.add(definition.name)
            }
            eventBus.publish(BeanCreatedEvent(definition.name, finalInstance, definition))
            return finalInstance
        } finally {
            synchronized(singletonCreationLock) { singletonsInCreation.remove(definition.name) }
        }
    }

    /**
     * 执行所有已初始化 singleton Bean 的 @PostEnable 方法
     */
    fun invokePostEnable() {
        debug("[IoC] 开始执行 @PostEnable 回调，共 ${initializationOrder.size} 个 singleton")
        val start = System.nanoTime()

        for (name in initializationOrder) {
            val definition = registry.getByName(name) ?: continue
            val instance = cycleResolver.getSingleton(name) ?: continue

            // 收集所有 @PostEnable 方法（包括实际类型上的）
            val methods = collectLifecycleMethods(definition, instance, PostEnable::class.java)
            for (postEnable in methods) {
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
        synchronized(singletonCreationLock) { singletonsInCreation.clear() }
        creationStack.remove()
        beanPostProcessors.clear()
        // 内置处理器不随重置丢失，保证语义与首次初始化一致
        beanPostProcessors.add(WrapWithPostProcessor)
        cycleResolver.clear()
    }

    /**
     * 关闭容器
     */
    fun shutdown() {
        debug("[IoC] 开始关闭容器，共 ${initializationOrder.size} 个 singleton 需要销毁")
        val start = System.nanoTime()

        for (name in initializationOrder.reversed()) {
            val definition = registry.getByName(name) ?: continue
            val instance = cycleResolver.getSingleton(name) ?: continue
            try {
                val methods = collectLifecycleMethods(definition, instance, PreDestroy::class.java)
                for (preDestroy in methods) {
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
                val processedInstance = initializeInstance(instance, definition)
                val populateMs = (System.nanoTime() - populateStart) / 1_000_000.0
                val postConstructMs = populateMs // initializeInstance 合并统计

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
                        "注入+初始化=${"%.2f".format(postConstructMs)}ms, " +
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

    /**
     * 对已实例化的对象执行「注入 → BeanPostProcessor(before) → @PostConstruct → BeanPostProcessor(after)」，
     * 返回最终（可能被处理器替换的）实例。
     *
     * 被 [createBean] 与 [registerExistingSingleton] 共用，保证两条路径的生命周期语义完全一致。
     */
    private fun initializeInstance(instance: Any, definition: BeanDefinition): Any {
        injector.populate(instance, definition)

        // BeanPostProcessor — before initialization
        var processedInstance = instance
        for (processor in beanPostProcessors) {
            processedInstance = processor.postProcessBeforeInitialization(processedInstance, definition.name)
        }

        injector.invokePostConstruct(processedInstance, definition)

        // BeanPostProcessor — after initialization
        for (processor in beanPostProcessors) {
            processedInstance = processor.postProcessAfterInitialization(processedInstance, definition.name)
        }

        return processedInstance
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
            // 检查循环中所有 Bean 的作用域
            val scopes = cycle.dropLast(1).mapNotNull { beanName ->
                definitionByName[beanName]?.scope
            }.toSet()
            
            val scopeInfo = if (scopes.size == 1) {
                val scope = BeanScopes.normalize(scopes.first())
                "[$scope]"
            } else {
                "[混合作用域: ${scopes.joinToString(", ")}]"
            }
            
            // 判断是否可解析
            val allSingleton = cycle.dropLast(1).all { beanName ->
                definitionByName[beanName]?.isSingletonScope() == true
            }
            
            if (allSingleton) {
                debug("[IoC] 检测到可由单例早期暴露处理的循环依赖 $scopeInfo: ${cycle.joinToString(" -> ")}")
            } else {
                warning("[IoC] 检测到无法解析的循环依赖 $scopeInfo: ${cycle.joinToString(" -> ")}")
            }
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

    /**
     * 收集生命周期方法。
     * 对于 @Bean 工厂方法产物，如果实际实例类型与声明返回类型不同，
     * 会补充扫描实际类型上的生命周期注解方法。
     */
    private fun collectLifecycleMethods(
        definition: BeanDefinition,
        instance: Any,
        annotationClass: Class<out Annotation>
    ): List<java.lang.reflect.Method> {
        val definedMethods = when (annotationClass) {
            PostEnable::class.java -> definition.postEnableMethods
            PreDestroy::class.java -> definition.preDestroyMethods
            else -> emptyList()
        }

        if (!definition.isFactoryBean()) return definedMethods

        val actualClass = instance.javaClass
        if (actualClass == definition.type) return definedMethods

        // 补充扫描实际类型上的方法
        val extraMethods = actualClass.declaredMethods.filter {
            it.isAnnotationPresent(annotationClass)
        }.filter { extra ->
            definedMethods.none { it.name == extra.name && it.parameterCount == extra.parameterCount }
        }.onEach { it.isAccessible = true }

        return definedMethods + extraMethods
    }
}

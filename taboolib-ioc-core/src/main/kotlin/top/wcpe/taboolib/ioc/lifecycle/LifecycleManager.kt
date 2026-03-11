package top.wcpe.taboolib.ioc.lifecycle

import taboolib.common.platform.function.debug
import taboolib.common.platform.function.warning
import top.wcpe.taboolib.ioc.bean.BeanDefinition
import top.wcpe.taboolib.ioc.bean.BeanRegistry
import top.wcpe.taboolib.ioc.bean.BeanScope
import top.wcpe.taboolib.ioc.bean.BeanScopes
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
    private val scopeLookup: (String) -> BeanScope? = { null }
) {

    private val initializationOrder = mutableListOf<String>()
    private val initializedSingletons = mutableSetOf<String>()
    private val singletonLocks = ConcurrentHashMap<String, Any>()
    private val creationStack = ThreadLocal.withInitial { ArrayDeque<String>() }

    /**
     * 初始化容器
     */
    fun initialize() {
        val definitions = registry.getAll().toList()
        validateScopes(definitions)
        logResolvableDependencyCycles(definitions.filter(BeanDefinition::isSingletonScope))

        val eagerDefinitions = definitions.filter(BeanDefinition::isEagerSingleton)
        debug("[IoC] 开始初始化容器，共 ${eagerDefinitions.size} 个预初始化 Bean")
        debug("[IoC] 预初始化 Bean: ${eagerDefinitions.map { it.name }}")

        eagerDefinitions.forEach { definition ->
            try {
                getOrCreateSingleton(definition)
            } catch (e: Exception) {
                warning("[IoC] Bean 初始化失败: ${definition.name} - ${e.message}")
                throw e
            }
        }

        debug("[IoC] 容器初始化完成")
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
     * 重置内部状态，不触发生命周期回调
     */
    fun resetState() {
        initializationOrder.clear()
        initializedSingletons.clear()
        singletonLocks.clear()
        creationStack.remove()
    }

    /**
     * 关闭容器
     */
    fun shutdown() {
        debug("[IoC] 开始关闭容器")

        for (name in initializationOrder.reversed()) {
            val definition = registry.getByName(name) ?: continue
            try {
                definition.preDestroy?.invoke(cycleResolver.getSingleton(name))
                debug("[IoC] Bean 销毁完成: $name")
            } catch (e: Exception) {
                warning("[IoC] Bean 销毁失败: $name - ${e.message}")
            }
        }

        resetState()
        debug("[IoC] 容器关闭完成")
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
            val instance = injector.instantiate(definition)
            if (cacheSingleton) {
                cycleResolver.addSingleton(definition.name, instance)
            }

            try {
                injector.populate(instance, definition)
                injector.invokePostConstruct(instance, definition)
            } catch (e: Exception) {
                if (cacheSingleton) {
                    cycleResolver.removeSingleton(definition.name)
                    initializedSingletons.remove(definition.name)
                }
                throw e
            }

            if (cacheSingleton && initializedSingletons.add(definition.name)) {
                initializationOrder.add(definition.name)
                debug("[IoC] Bean 初始化完成: ${definition.name}")
            }
            return instance
        } finally {
            stack.removeLast()
        }
    }

    private fun validateScopes(definitions: List<BeanDefinition>) {
        definitions.forEach { definition ->
            val scope = BeanScopes.normalize(definition.scope)
            if (!BeanScopes.isStandard(scope) && scopeLookup(scope) == null) {
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
}

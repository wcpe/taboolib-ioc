package top.wcpe.taboolib.ioc.lifecycle

import taboolib.common.platform.function.debug
import taboolib.common.platform.function.warning
import top.wcpe.taboolib.ioc.bean.BeanDefinition
import top.wcpe.taboolib.ioc.bean.InjectParameter
import top.wcpe.taboolib.ioc.bean.BeanRegistry
import top.wcpe.taboolib.ioc.cycle.CircularDependencyException
import top.wcpe.taboolib.ioc.cycle.CycleDetector
import top.wcpe.taboolib.ioc.cycle.CycleResolver
import top.wcpe.taboolib.ioc.inject.Injector

/**
 * 生命周期管理器。
 *
 * 采用两阶段装配：
 * 1. 先按构造依赖顺序实例化所有 Bean
 * 2. 再统一执行字段/方法注入与 PostConstruct
 */
class LifecycleManager(
    private val registry: BeanRegistry,
    private val cycleResolver: CycleResolver,
    private val injector: Injector,
    private val cycleDetector: CycleDetector
) {

    // 保存初始化顺序，用于逆序销毁
    private val initializationOrder = mutableListOf<String>()

    /**
     * 初始化容器
     */
    fun initialize() {
        val definitions = registry.getAll().toList()
        ensureConstructorCyclesResolved(definitions)
        logResolvableDependencyCycles(definitions)
        val constructorOrder = topologicalSortByConstructor(definitions)
        val lifecycleOrder = topologicalSortByDependencies(definitions)

        debug("[IoC] 开始初始化容器，共 ${constructorOrder.size} 个 Bean")
        debug("[IoC] 实例化顺序: ${constructorOrder.map { it.name }}")

        for (definition in constructorOrder) {
            try {
                val instance = injector.instantiate(definition)
                cycleResolver.addSingleton(definition.name, instance)
                debug("[IoC] Bean 实例创建完成: ${definition.name}")
            } catch (e: Exception) {
                warning("[IoC] Bean 实例创建失败: ${definition.name} - ${e.message}")
                throw e
            }
        }

        for (definition in constructorOrder) {
            val instance = cycleResolver.getSingleton(definition.name) ?: continue
            try {
                injector.populate(instance, definition)
                debug("[IoC] Bean 依赖装配完成: ${definition.name}")
            } catch (e: Exception) {
                warning("[IoC] Bean 依赖装配失败: ${definition.name} - ${e.message}")
                throw e
            }
        }

        debug("[IoC] 生命周期回调顺序: ${lifecycleOrder.map { it.name }}")
        for (definition in lifecycleOrder) {
            val instance = cycleResolver.getSingleton(definition.name) ?: continue
            try {
                injector.invokePostConstruct(instance, definition)
                initializationOrder.add(definition.name)
                debug("[IoC] Bean 初始化完成: ${definition.name}")
            } catch (e: Exception) {
                warning("[IoC] Bean 初始化失败: ${definition.name} - ${e.message}")
                throw e
            }
        }

        debug("[IoC] 容器初始化完成")
    }

    /**
     * 关闭容器
     */
    fun shutdown() {
        debug("[IoC] 开始关闭容器")

        // 逆序调用 @PreDestroy
        for (name in initializationOrder.reversed()) {
            val definition = registry.getByName(name) ?: continue
            try {
                definition.preDestroy?.invoke(cycleResolver.getSingleton(name))
                debug("[IoC] Bean 销毁完成: $name")
            } catch (e: Exception) {
                warning("[IoC] Bean 销毁失败: $name - ${e.message}")
            }
        }

        initializationOrder.clear()
        debug("[IoC] 容器关闭完成")
    }

    private fun topologicalSortByConstructor(definitions: List<BeanDefinition>): List<BeanDefinition> {
        val definitionByName = definitions.associateBy { it.name }
        val visited = mutableSetOf<String>()
        val sorted = mutableListOf<BeanDefinition>()

        fun visit(definition: BeanDefinition) {
            if (definition.name in visited) {
                return
            }
            definition.constructorParameters.forEach { dependency ->
                val dependencyDefinition = resolveDependencyDefinition(dependency.type, dependency.nameQualifier, definitionByName)
                if (dependencyDefinition != null && dependencyDefinition.name != definition.name) {
                    visit(dependencyDefinition)
                }
            }
            visited += definition.name
            sorted += definition
        }

        definitions.forEach(::visit)
        return sorted
    }

    private fun topologicalSortByDependencies(definitions: List<BeanDefinition>): List<BeanDefinition> {
        val definitionByName = definitions.associateBy { it.name }
        val visited = mutableSetOf<String>()
        val visiting = mutableSetOf<String>()
        val sorted = mutableListOf<BeanDefinition>()

        fun visit(definition: BeanDefinition) {
            if (definition.name in visited) {
                return
            }
            if (!visiting.add(definition.name)) {
                return
            }

            definition.dependencies.forEach { dependency ->
                val dependencyDefinition = resolveDependencyDefinition(dependency.type, dependency.nameQualifier, definitionByName)
                if (dependencyDefinition != null && dependencyDefinition.name != definition.name) {
                    visit(dependencyDefinition)
                }
            }

            visiting.remove(definition.name)
            visited += definition.name
            sorted += definition
        }

        definitions.forEach(::visit)
        return sorted
    }

    private fun resolveDependencyDefinition(
        type: Class<*>,
        name: String?,
        definitionByName: Map<String, BeanDefinition>
    ): BeanDefinition? {
        return if (name != null) {
            definitionByName[name]
        } else {
            registry.getPrimaryByType(type)
        }
    }

    private fun ensureConstructorCyclesResolved(definitions: List<BeanDefinition>) {
        val definitionByName = definitions.associateBy { it.name }
        val cycle = cycleDetector.findFirstCycle(
            nodes = definitions,
            nameOf = { it.name },
            dependenciesOf = { definition ->
                resolveDependencyDefinitions(definition.constructorParameters, definitionByName)
            }
        ) ?: return

        throw CircularDependencyException(cycle.last(), cycle)
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
            debug("[IoC] 检测到可由两阶段装配处理的循环依赖: ${cycle.joinToString(" -> ")}")
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

package top.wcpe.taboolib.ioc.lifecycle

import taboolib.common.platform.function.debug
import taboolib.common.platform.function.warning
import top.wcpe.taboolib.ioc.bean.BeanDefinition
import top.wcpe.taboolib.ioc.bean.BeanRegistry
import top.wcpe.taboolib.ioc.cycle.CycleResolver
import top.wcpe.taboolib.ioc.inject.Injector

/**
 * 生命周期管理器
 */
class LifecycleManager(
    private val registry: BeanRegistry,
    private val cycleResolver: CycleResolver,
    private val injector: Injector
) {

    // 保存初始化顺序，用于逆序销毁
    private val initializationOrder = mutableListOf<String>()

    /**
     * 初始化容器
     */
    fun initialize() {
        val definitions = registry.getAll().toList()
        val sorted = topologicalSort(definitions)

        debug("[IoC] 开始初始化容器，共 ${sorted.size} 个 Bean")
        debug("[IoC] 初始化顺序: ${sorted.map { it.name }}")

        for (definition in sorted) {
            try {
                injector.createAndInject(definition)
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
                definition.preDestroy?.invoke(cycleResolver.getSingleton(name).first)
                debug("[IoC] Bean 销毁完成: $name")
            } catch (e: Exception) {
                warning("[IoC] Bean 销毁失败: $name - ${e.message}")
            }
        }

        initializationOrder.clear()
        debug("[IoC] 容器关闭完成")
    }

    /**
     * 拓扑排序 Bean 定义
     */
    private fun topologicalSort(definitions: List<BeanDefinition>): List<BeanDefinition> {
        val visited = mutableSetOf<String>()
        val visiting = mutableSetOf<String>()
        val sorted = mutableListOf<BeanDefinition>()

        fun visit(def: BeanDefinition) {
            if (def.name in visited) return
            if (def.name in visiting) return // 循环依赖，跳过

            visiting.add(def.name)

            // 遍历依赖
            for (field in def.injectFields) {
                val depDef = registry.getPrimaryByType(field.requiredType)
                if (depDef != null && depDef.name != def.name) {
                    visit(depDef)
                }
            }

            visiting.remove(def.name)
            visited.add(def.name)
            sorted.add(def)
        }

        for (def in definitions) {
            visit(def)
        }

        return sorted
    }
}

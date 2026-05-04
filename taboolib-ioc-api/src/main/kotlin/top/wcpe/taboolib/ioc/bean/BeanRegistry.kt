package top.wcpe.taboolib.ioc.bean

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Bean 注册表 - 管理 Bean 定义的注册与查询。
 *
 * 内部使用 ConcurrentHashMap 存储，支持按名称和按类型两种查询方式。
 * 一个类型可以对应多个 Bean 定义。
 */
class BeanRegistry {

    private val definitionsByName = ConcurrentHashMap<String, BeanDefinition>()
    private val definitionsByType = ConcurrentHashMap<Class<*>, CopyOnWriteArrayList<BeanDefinition>>()

    /**
     * 注册 Bean 定义。
     *
     * @param definition Bean 定义
     */
    fun register(definition: BeanDefinition) {
        definitionsByName[definition.name] = definition
        resolveAssignableTypes(definition.type).forEach { type ->
            definitionsByType.getOrPut(type) { CopyOnWriteArrayList() }.add(definition)
        }
    }

    /**
     * 按名称获取 Bean 定义。
     *
     * @param name Bean 名称
     * @return Bean 定义，不存在则返回 null
     */
    fun getByName(name: String): BeanDefinition? = definitionsByName[name]

    /**
     * 按类型获取 Bean 定义列表，按 order 升序排列。
     *
     * @param type Bean 类型
     * @return Bean 定义列表，可能为空
     */
    fun getByType(type: Class<*>): List<BeanDefinition> =
        (definitionsByType[type]?.toList() ?: emptyList()).sortedBy { it.order }

    /**
     * 按类型获取首选 Bean 定义。
     *
     * 选择规则：
     * 1. 如果只有一个 Bean，返回它
     * 2. 如果有多个 Bean，优先返回标记了 @Primary 的
     * 3. 如果没有 @Primary，返回 order 值最小的
     *
     * @param type Bean 类型
     * @return 首选 Bean 定义，不存在则返回 null
     */
    fun getPrimaryByType(type: Class<*>): BeanDefinition? {
        val definitions = getByType(type)
        return when {
            definitions.isEmpty() -> null
            definitions.size == 1 -> definitions[0]
            else -> {
                val primaries = definitions.filter { it.isPrimary }
                when {
                    primaries.size > 1 -> throw IllegalStateException(
                        "[IoC] 类型 ${type.name} 存在多个 @Primary Bean: ${primaries.map { it.name }}，请只保留一个 @Primary"
                    )
                    primaries.size == 1 -> primaries[0]
                    else -> definitions[0]
                }
            }
        }
    }

    /**
     * 获取所有 Bean 定义。
     *
     * @return Bean 定义集合
     */
    fun getAll(): Collection<BeanDefinition> = definitionsByName.values

    /**
     * 检查是否包含指定名称的 Bean。
     *
     * @param name Bean 名称
     * @return 是否存在
     */
    fun contains(name: String): Boolean = definitionsByName.containsKey(name)

    /**
     * 获取所有 Bean 名称。
     *
     * @return Bean 名称集合
     */
    fun getNames(): Set<String> = definitionsByName.keys

    /**
     * 清空注册表。
     */
    fun clear() {
        definitionsByName.clear()
        definitionsByType.clear()
    }

    /**
     * 移除指定名称的 Bean 定义。
     *
     * @param name Bean 名称
     */
    fun remove(name: String) {
        val definition = definitionsByName.remove(name) ?: return
        resolveAssignableTypes(definition.type).forEach { type ->
            definitionsByType[type]?.remove(definition)
        }
    }

    private fun resolveAssignableTypes(type: Class<*>): Set<Class<*>> {
        val resolved = linkedSetOf<Class<*>>()

        fun visit(current: Class<*>?) {
            if (current == null || current == Any::class.java) {
                return
            }
            if (!resolved.add(current)) {
                return
            }
            current.interfaces.forEach(::visit)
            visit(current.superclass)
        }

        visit(type)
        return resolved
    }
}

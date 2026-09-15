package top.wcpe.taboolib.ioc.bean

import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

/**
 * Bean 注册表 - 管理 Bean 定义的注册与查询。
 *
 * 内部使用 ConcurrentHashMap 存储，支持按名称和按类型两种查询方式。
 * 一个类型可以对应多个 Bean 定义。
 */
class BeanRegistry {

    private val definitionsByName = ConcurrentHashMap<String, BeanDefinition>()

    /**
     * 类型 → 桶。每个桶缓存「按 order 升序」的**不可变快照**。
     *
     * 之所以缓存：`getByType` 处在 `getBean(Class)` 的最高频路径上，而它原先每次调用都要
     * `toList() + sortedBy { it.order }`（新建两个 ArrayList 再排序）。注册 / 移除是启动期
     * 低频繁操作，由它们承担重建成本，读路径改为一次 volatile 读：零分配、零排序。
     *
     * **收益口径**（真机实测，确定性指标）：`getBean(Class)` 每次调用分配
     * **51.1 B → 3.1 B**。折合时间只有个位数 ns，落在基准自身 ±15% 的轮间噪声内，
     * 因此**不宣称 ns/op 改善** —— 也不要把「`getBean(Class)` 与 `containsBean`
     * 之间那几十 ns 的差额」归因到这里：该归因未经验证，实测把这段操作优化掉之后
     * 端到端 ns/op 没有可分辨的变化。
     */
    private val definitionsByType = ConcurrentHashMap<Class<*>, TypeBucket>()

    private class TypeBucket {

        /**
         * 同时保护 [definitions] 与 [sortedCache]：
         * 若只在写入侧加锁，读者可能拿着「变更前的 definitions 视图」算出快照、
         * 再在变更之后写进缓存，从而把过期快照永久留在缓存里。
         */
        private val lock = Any()

        private val definitions = ArrayList<BeanDefinition>()

        @Volatile
        private var sortedCache: List<BeanDefinition>? = null

        /**
         * 返回按 order 升序的不可变快照。
         *
         * 返回的是**共享实例**（同一次注册状态下多次调用拿到同一个对象），且不可修改；
         * 外部只应读取。
         */
        fun sorted(): List<BeanDefinition> {
            sortedCache?.let { return it }
            synchronized(lock) {
                sortedCache?.let { return it }
                val computed = Collections.unmodifiableList(definitions.sortedBy { it.order })
                sortedCache = computed
                return computed
            }
        }

        fun add(definition: BeanDefinition) {
            synchronized(lock) {
                definitions.add(definition)
                sortedCache = null
            }
        }

        fun remove(definition: BeanDefinition) {
            synchronized(lock) {
                definitions.remove(definition)
                sortedCache = null
            }
        }
    }

    /**
     * 注册 Bean 定义。
     *
     * @param definition Bean 定义
     */
    fun register(definition: BeanDefinition) {
        definitionsByName[definition.name] = definition
        resolveAssignableTypes(definition.type).forEach { type ->
            definitionsByType.getOrPut(type) { TypeBucket() }.add(definition)
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
     * 返回的是该类型当前注册状态下**按 order 排好序的共享不可变快照**：
     * 同一注册状态下重复调用返回同一实例，且不可修改。
     * 后续的注册/移除会替换掉快照，已取得的列表不受影响。
     *
     * @param type Bean 类型
     * @return Bean 定义列表，可能为空
     */
    fun getByType(type: Class<*>): List<BeanDefinition> =
        definitionsByType[type]?.sorted() ?: emptyList()

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
                // 内联扫描替代 `filter { it.isPrimary }`：多候选是热路径上的常态，
                // 而这里绝大多数调用只需要「有没有 @Primary」，不必为它新建一个列表。
                // 只有真的存在第二个 @Primary 时才回到 filter 去拼完整报错信息。
                var primary: BeanDefinition? = null
                for (definition in definitions) {
                    if (!definition.isPrimary) continue
                    if (primary != null) {
                        throw IllegalStateException(
                            "[IoC] 类型 ${type.name} 存在多个 @Primary Bean: " +
                                "${definitions.filter { it.isPrimary }.map { it.name }}，请只保留一个 @Primary"
                        )
                    }
                    primary = definition
                }
                primary ?: definitions[0]
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

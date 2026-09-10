package top.wcpe.taboolib.ioc.bean

import taboolib.common.platform.function.warning
import java.util.concurrent.ConcurrentHashMap

/**
 * Bean 解析器 — 封装 Bean 查找与作用域解析的共享逻辑。
 *
 * BeanContainer 和 IocTestContext 都委托给此类，消除重复代码。
 */
class BeanResolver(
    private val registry: BeanRegistry,
    private val manualBeans: ConcurrentHashMap<String, Any>,
    private val singletonProvider: (BeanDefinition) -> Any,
    private val transientProvider: (BeanDefinition) -> Any,
    private val scopeLookup: (String) -> BeanScope?
) {

    @Suppress("UNCHECKED_CAST")
    fun <T> getBean(type: Class<T>, name: String?): T? {
        if (name != null) {
            manualBeans[name]?.takeIf { type.isInstance(it) }?.let { return it as T }
        }
        val raw = resolveBean(type, name) ?: resolveManualBean(type) ?: return null
        // 出口类型校验：AOP 代理（JDK 动态代理只实现接口）注入到具体类类型的注入点时，
        // 必须在这里抛出带修复指引的类型化异常，而不是让擦除的 as? T 放行、
        // 把 ClassCastException 推迟到业务调用点（与 IoC 毫无关联，极难定位）。
        if (!type.isInstance(raw)) {
            throw BeanNotOfRequiredTypeException(type, raw.javaClass, name)
        }
        return raw as T
    }

    @Suppress("UNCHECKED_CAST")
    fun <T> getBeansOfType(type: Class<T>): List<T> {
        val registeredBeans = registry.getByType(type).mapNotNull { def ->
            try {
                getBean(type, def.name)
            } catch (e: BeanNotOfRequiredTypeException) {
                // 枚举语义：跳过与请求类型不兼容的（已被 AOP 代理包装的）Bean，并明确告警，
                // 不让单个不兼容 Bean 破坏整个枚举调用
                warning("[IoC] getBeansOfType 跳过类型不兼容的 Bean: ${e.message}")
                null
            }
        }
        val manual = manualBeans.values
            .filter { type.isInstance(it) }
            .map { type.cast(it) }
        return (registeredBeans + manual).distinctBy { System.identityHashCode(it) }
    }

    fun containsBean(name: String): Boolean =
        manualBeans.containsKey(name) || registry.contains(name)

    /**
     * 返回容器中所有 Bean 名称的**并集**：
     * - 来自 [BeanRegistry] 的定义名（扫描 / 工厂方法注册）；
     * - 来自 [manualBeans] 的显式手动注册名。
     *
     * 两者语义不同（定义 vs 实例），但对外统一为「可解析的 Bean 名称集合」，
     * 因此此处取并集而非交集。注意：若同一名称同时存在于两处，仅出现一次（Set 语义）。
     */
    fun getBeanNames(): Set<String> =
        registry.getNames() + manualBeans.keys

    private fun resolveBean(type: Class<*>, name: String?): Any? {
        val definition = if (name != null) {
            registry.getByName(name)
        } else {
            registry.getPrimaryByType(type)
        } ?: return null

        if (!type.isAssignableFrom(definition.type)) return null

        return when {
            definition.isSingletonScope() -> singletonProvider(definition)
            definition.isPrototypeScope() -> transientProvider(definition)
            else -> {
                val scope = scopeLookup(definition.scope)
                    ?: throw IllegalStateException("未注册的 Bean 作用域: ${definition.scope} (${definition.name})")
                scope.get(definition.name, definition) { transientProvider(definition) }
            }
        }
    }

    private fun resolveManualBean(type: Class<*>): Any? {
        return manualBeans.values.firstOrNull { type.isInstance(it) }
    }
}

package top.wcpe.taboolib.ioc.bean

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
        return (resolveBean(type, name) ?: resolveManualBean(type)) as? T
    }

    @Suppress("UNCHECKED_CAST")
    fun <T> getBeansOfType(type: Class<T>): List<T> {
        val registeredBeans = registry.getByType(type).mapNotNull { def ->
            getBean(type, def.name)
        }
        val manual = manualBeans.values
            .filter { type.isInstance(it) }
            .map { type.cast(it) }
        return (registeredBeans + manual).distinctBy { System.identityHashCode(it) }
    }

    fun containsBean(name: String): Boolean =
        manualBeans.containsKey(name) || registry.contains(name)

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

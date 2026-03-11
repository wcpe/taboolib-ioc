package top.wcpe.taboolib.ioc.bean

import taboolib.common.platform.function.debug
import taboolib.common.platform.function.warning
import top.wcpe.taboolib.ioc.cycle.CycleDetector
import top.wcpe.taboolib.ioc.cycle.CycleResolver
import top.wcpe.taboolib.ioc.inject.ConstructorResolver
import top.wcpe.taboolib.ioc.inject.FieldInjector
import top.wcpe.taboolib.ioc.inject.Injector
import top.wcpe.taboolib.ioc.lifecycle.LifecycleManager
import top.wcpe.taboolib.ioc.scan.ClassScanner
import java.util.concurrent.ConcurrentHashMap

/**
 * Bean 容器 - IoC 容器的主要入口点。
 *
 * 提供获取、注册和检查 Bean 的方法。容器会在 Taboolib 的 ACTIVE 生命周期阶段自动初始化。
 *
 * ## 使用示例
 *
 * ```kotlin
 * // 按类型获取 Bean
 * val userService = BeanContainer.getBean(UserService::class.java)
 *
 * // 按名称获取 Bean
 * val service = BeanContainer.getBean(UserService::class.java, "myService")
 *
 * // 获取某类型的所有 Bean
 * val allServices = BeanContainer.getBeansOfType(UserService::class.java)
 *
 * // 手动注册 Bean
 * BeanContainer.registerBean("dataSource", dataSource)
 * ```
 *
 * @see top.wcpe.taboolib.ioc.annotation.Component
 * @see top.wcpe.taboolib.ioc.annotation.Inject
 */
object BeanContainer {

    private val registry = BeanRegistry()
    private val manualBeansByName = ConcurrentHashMap<String, Any>()
    private val cycleDetector = CycleDetector()
    private val cycleResolver = CycleResolver()
    private val constructorResolver = ConstructorResolver()
    private val scanner = ClassScanner(registry, constructorResolver)
    private val fieldInjector = FieldInjector(registry) { type, name ->
        getBean(type, name)
    }
    private val injector = Injector(
        registry, cycleResolver, fieldInjector
    )
    private val lifecycleManager = LifecycleManager(registry, cycleResolver, injector, cycleDetector)

    /**
     * 容器是否已初始化。
     *
     * 容器会在 Taboolib 的 ACTIVE 生命周期阶段自动初始化。
     */
    @Volatile
    var initialized = false
        private set

    @Volatile
    private var initializing = false

    /**
     * 获取 Bean 实例。
     *
     * @param T Bean 类型
     * @param type Bean 的类型
     * @param name Bean 的名称（可选），指定后按名称匹配
     * @return Bean 实例，如果不存在则返回 null
     */
    @Suppress("UNCHECKED_CAST")
    fun <T> getBean(type: Class<T>, name: String? = null): T? {
        if (!initialized && !initializing) {
            warning("[IoC] 容器未初始化")
            return null
        }

        if (name != null) {
            manualBeansByName[name]?.takeIf { type.isInstance(it) }?.let {
                @Suppress("UNCHECKED_CAST")
                return it as T
            }
        }

        return (resolveBean(type, name) ?: resolveManualBean(type)) as? T
    }

    /**
     * 获取所有指定类型的 Bean 实例。
     *
     * @param T Bean 类型
     * @param type Bean 的类型
     * @return Bean 实例列表
     */
    @Suppress("UNCHECKED_CAST")
    fun <T> getBeansOfType(type: Class<T>): List<T> {
        if (!initialized) return emptyList()

        val registeredBeans = registry.getByType(type).mapNotNull { def ->
            getBean(type, def.name)
        }
        val manualBeans = manualBeansByName.values
            .filter { type.isInstance(it) }
            .map { type.cast(it) }

        return (registeredBeans + manualBeans).distinctBy { System.identityHashCode(it) }
    }

    /**
     * 检查是否包含指定名称的 Bean。
     *
     * @param name Bean 名称
     * @return 是否存在该名称的 Bean
     */
    fun containsBean(name: String): Boolean = manualBeansByName.containsKey(name) || registry.contains(name)

    /**
     * 获取所有 Bean 的名称列表。
     *
     * @return Bean 名称集合
     */
    fun getBeanNames(): Set<String> = registry.getNames() + manualBeansByName.keys

    /**
     * 手动注册一个 Bean 实例。
     *
     * 用于动态注册第三方库的对象或需要手动创建的实例。
     *
     * @param name Bean 名称
     * @param instance Bean 实例
     */
    fun registerBean(name: String, instance: Any) {
        manualBeansByName[name] = instance
        cycleResolver.addSingleton(name, instance)
        debug("[IoC] 手动注册 Bean: $name")
    }

    /**
     * 初始化容器
     */
    internal fun initialize() {
        if (initialized) return

        debug("[IoC] 开始初始化容器，共 ${registry.getAll().size} 个 Bean 定义")

        initializing = true
        try {
            lifecycleManager.initialize()
            initialized = true
        } finally {
            initializing = false
        }
    }

    /**
     * 关闭容器
     */
    internal fun shutdown() {
        if (!initialized) return

        lifecycleManager.shutdown()

        cycleResolver.clear()
        registry.clear()
        manualBeansByName.clear()
        initialized = false

        debug("[IoC] 容器已关闭")
    }

    /**
     * 获取注册表（供 ComponentVisitor 使用）
     */
    internal fun getRegistry(): BeanRegistry = registry

    /**
     * 获取扫描器（供 ComponentVisitor 使用）
     */
    internal fun getScanner(): ClassScanner = scanner

    internal fun resetForTesting() {
        cycleResolver.clear()
        registry.clear()
        manualBeansByName.clear()
        initialized = false
        initializing = false
    }

    private fun resolveBean(type: Class<*>, name: String?): Any? {
        val definition = if (name != null) {
            registry.getByName(name)
        } else {
            registry.getPrimaryByType(type)
        } ?: return null

        if (!type.isAssignableFrom(definition.type)) {
            return null
        }

        return cycleResolver.getSingleton(definition.name)
    }

    private fun resolveManualBean(type: Class<*>): Any? {
        return manualBeansByName.values.firstOrNull { type.isInstance(it) }
    }
}

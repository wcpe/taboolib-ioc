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
 * val userService = BeanContainer.getBean(UserService::class.java)
 * val service = BeanContainer.getBean(UserService::class.java, "myService")
 * val allServices = BeanContainer.getBeansOfType(UserService::class.java)
 * BeanContainer.registerBean("dataSource", dataSource)
 * ```
 */
object BeanContainer {

    private val registry = BeanRegistry()
    private val manualBeansByName = ConcurrentHashMap<String, Any>()
    private val customScopes = ConcurrentHashMap<String, BeanScope>()
    private val cycleDetector = CycleDetector()
    private val cycleResolver = CycleResolver()
    private val constructorResolver = ConstructorResolver()
    private val scanner = ClassScanner(constructorResolver)
    private val fieldInjector = FieldInjector(registry) { type, name ->
        getBean(type, name)
    }
    private val injector = Injector(fieldInjector) { type, name ->
        getBean(type, name)
    }
    private val lifecycleManager = LifecycleManager(
        registry = registry,
        cycleResolver = cycleResolver,
        injector = injector,
        cycleDetector = cycleDetector,
        scopeLookup = ::getScope
    )

    @Volatile
    var initialized = false
        private set

    @Volatile
    private var initializing = false

    @Suppress("UNCHECKED_CAST")
    fun <T> getBean(type: Class<T>, name: String? = null): T? {
        if (!initialized && !initializing) {
            warning("[IoC] 容器未初始化")
            return null
        }

        if (name != null) {
            manualBeansByName[name]?.takeIf { type.isInstance(it) }?.let {
                return it as T
            }
        }

        return (resolveBean(type, name) ?: resolveManualBean(type)) as? T
    }

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

    fun containsBean(name: String): Boolean = manualBeansByName.containsKey(name) || registry.contains(name)

    fun getBeanNames(): Set<String> = registry.getNames() + manualBeansByName.keys

    fun registerBean(name: String, instance: Any) {
        manualBeansByName[name] = instance
        cycleResolver.addSingleton(name, instance)
        debug("[IoC] 手动注册 Bean: $name")
    }

    fun registerScope(name: String, scope: BeanScope) {
        val normalized = BeanScopes.normalize(name)
        require(!BeanScopes.isStandard(normalized)) { "标准作用域不允许被覆盖: $name" }
        customScopes[normalized] = scope
        debug("[IoC] 注册自定义作用域: $normalized")
    }

    /**
     * 为 object 类的 @Lazy 字段注入延迟代理。
     * 供 ObjectInjector 调用，委托给内部的 FieldInjector。
     */
    internal fun injectLazyObjectField(instance: Any, type: Class<*>, nameQualifier: String?, field: java.lang.reflect.Field) {
        fieldInjector.injectLazyField(instance, type, nameQualifier, field)
    }

    internal fun initialize() {
        if (initialized) return

        val start = System.nanoTime()
        debug("[IoC] 开始初始化容器，共 ${registry.getAll().size} 个 Bean 定义")

        initializing = true
        try {
            lifecycleManager.initialize()
            initialized = true
        } finally {
            initializing = false
        }

        val ms = (System.nanoTime() - start) / 1_000_000.0
        debug("[IoC] BeanContainer 初始化完成，总耗时 ${"%.2f".format(ms)}ms")
    }

    internal fun shutdown() {
        if (!initialized) return

        val start = System.nanoTime()
        lifecycleManager.shutdown()
        clearScopes()
        cycleResolver.clear()
        registry.clear()
        manualBeansByName.clear()
        initialized = false

        val ms = (System.nanoTime() - start) / 1_000_000.0
        debug("[IoC] 容器已关闭，总耗时 ${"%.2f".format(ms)}ms")
    }

    internal fun getRegistry(): BeanRegistry = registry

    internal fun getScanner(): ClassScanner = scanner

    internal fun resetForTesting() {
        lifecycleManager.resetState()
        clearScopes()
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

        return when {
            definition.isSingletonScope() -> lifecycleManager.getOrCreateSingleton(definition)
            definition.isPrototypeScope() -> lifecycleManager.createTransient(definition)
            else -> resolveCustomScopedBean(definition)
        }
    }

    private fun resolveCustomScopedBean(definition: BeanDefinition): Any {
        val scope = getScope(definition.scope)
            ?: throw IllegalStateException("未注册的 Bean 作用域: ${definition.scope} (${definition.name})")

        return scope.get(definition.name, definition) {
            lifecycleManager.createTransient(definition)
        }
    }

    private fun resolveManualBean(type: Class<*>): Any? {
        return manualBeansByName.values.firstOrNull { type.isInstance(it) }
    }

    private fun clearScopes() {
        customScopes.values.forEach { scope ->
            runCatching { scope.clear() }
                .onFailure { warning("[IoC] 清理自定义作用域失败: ${it.message}") }
        }
        customScopes.clear()
    }

    private fun getScope(name: String): BeanScope? {
        return customScopes[BeanScopes.normalize(name)]
    }
}


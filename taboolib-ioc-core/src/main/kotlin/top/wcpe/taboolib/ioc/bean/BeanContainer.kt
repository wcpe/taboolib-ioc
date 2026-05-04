package top.wcpe.taboolib.ioc.bean

import taboolib.common.platform.function.debug
import taboolib.common.platform.function.warning
import top.wcpe.taboolib.ioc.annotation.ConditionContext
import top.wcpe.taboolib.ioc.aop.AdvisorRegistry
import top.wcpe.taboolib.ioc.aop.AopProxyFactory
import top.wcpe.taboolib.ioc.aop.AspectScanner
import top.wcpe.taboolib.ioc.cycle.CycleDetector
import top.wcpe.taboolib.ioc.cycle.CycleResolver
import top.wcpe.taboolib.ioc.inject.ConstructorResolver
import top.wcpe.taboolib.ioc.inject.FieldInjector
import top.wcpe.taboolib.ioc.inject.Injector
import top.wcpe.taboolib.ioc.inject.ValueResolver
import top.wcpe.taboolib.ioc.lifecycle.EventBus
import top.wcpe.taboolib.ioc.lifecycle.LifecycleManager
import top.wcpe.taboolib.ioc.scan.ClassScanner
import top.wcpe.taboolib.ioc.scope.RefreshBeanScope
import top.wcpe.taboolib.ioc.scope.ThreadBeanScope
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
    private val advisorRegistry = AdvisorRegistry()
    private val aopProxyFactory = AopProxyFactory(advisorRegistry)
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
        scopeLookup = ::getScope,
        aopProxyFactory = aopProxyFactory
    )

    @Volatile
    var initialized = false
        private set

    @Volatile
    private var initializing = false

    private val resolver by lazy {
        BeanResolver(
            registry = registry,
            manualBeans = manualBeansByName,
            singletonProvider = lifecycleManager::getOrCreateSingleton,
            transientProvider = lifecycleManager::createTransient,
            scopeLookup = ::getScope
        )
    }

    @Suppress("UNCHECKED_CAST")
    fun <T> getBean(type: Class<T>, name: String? = null): T? {
        if (!initialized && !initializing) {
            warning("[IoC] 容器未初始化")
            return null
        }
        return resolver.getBean(type, name)
    }

    fun <T> getBeansOfType(type: Class<T>): List<T> {
        if (!initialized) return emptyList()
        return resolver.getBeansOfType(type)
    }

    fun containsBean(name: String): Boolean = resolver.containsBean(name)

    fun getBeanNames(): Set<String> = resolver.getBeanNames()

    /**
     * 手动注册 Bean 实例。
     * 
     * 注册的 Bean 会经过完整的生命周期处理：
     * - 属性注入（@Inject 字段和方法）
     * - BeanPostProcessor 回调
     * - @PostConstruct 回调
     * - AOP 代理包装
     * - @PreDestroy 回调（容器关闭时）
     * 
     * @param name Bean 名称
     * @param instance Bean 实例
     */
    fun registerBean(name: String, instance: Any) {
        if (!initialized && !initializing) {
            // 容器未初始化时，直接存储，等待容器初始化后处理
            manualBeansByName[name] = instance
            cycleResolver.addSingleton(name, instance)
            debug("[IoC] 手动注册 Bean（待处理）: $name")
            return
        }

        // 容器已初始化，执行完整生命周期
        val definition = createManualBeanDefinition(name, instance)
        registry.register(definition)
        
        try {
            // 执行属性注入
            injector.populate(instance, definition)
            
            // BeanPostProcessor — before initialization
            var processedInstance = instance
            for (processor in lifecycleManager.getBeanPostProcessors()) {
                processedInstance = processor.postProcessBeforeInitialization(processedInstance, name)
            }
            
            // 执行 @PostConstruct 回调
            injector.invokePostConstruct(processedInstance, definition)
            
            // BeanPostProcessor — after initialization
            for (processor in lifecycleManager.getBeanPostProcessors()) {
                processedInstance = processor.postProcessAfterInitialization(processedInstance, name)
            }
            
            // AOP 代理包装
            val finalInstance = if (aopProxyFactory != null) {
                val proxy = aopProxyFactory.wrapIfNecessary(processedInstance, definition.type)
                if (proxy !== processedInstance) {
                    debug("[IoC] 手动注册的 Bean 已包装 AOP 代理: $name")
                }
                proxy
            } else {
                processedInstance
            }
            
            // 缓存到容器
            manualBeansByName[name] = finalInstance
            cycleResolver.addSingleton(name, finalInstance)
            
            // 记录初始化顺序（用于 @PreDestroy）
            lifecycleManager.recordInitialization(name)
            
            debug("[IoC] 手动注册 Bean（已完成生命周期）: $name")
        } catch (e: Exception) {
            registry.remove(name)
            manualBeansByName.remove(name)
            cycleResolver.removeSingleton(name)
            throw IllegalStateException("手动注册 Bean 失败: $name", e)
        }
    }
    
    /**
     * 为手动注册的 Bean 创建 BeanDefinition。
     * 扫描实例类上的注入点和生命周期方法。
     */
    private fun createManualBeanDefinition(name: String, instance: Any): BeanDefinition {
        val clazz = instance.javaClass
        
        // 扫描 @Inject 字段
        val injectFields = scanner.scanInjectFields(clazz)
        
        // 扫描 @Inject 方法
        val injectMethods = scanner.scanInjectMethods(clazz)
        
        // 扫描 @Value 字段
        val valueFields = scanner.scanValueFields(clazz)
        
        // 扫描生命周期方法
        val postConstructMethods = clazz.declaredMethods.filter {
            it.isAnnotationPresent(top.wcpe.taboolib.ioc.annotation.PostConstruct::class.java)
        }.onEach { it.isAccessible = true }
        
        val postEnableMethods = clazz.declaredMethods.filter {
            it.isAnnotationPresent(top.wcpe.taboolib.ioc.annotation.PostEnable::class.java)
        }.onEach { it.isAccessible = true }
        
        val preDestroyMethods = clazz.declaredMethods.filter {
            it.isAnnotationPresent(top.wcpe.taboolib.ioc.annotation.PreDestroy::class.java)
        }.onEach { it.isAccessible = true }
        
        // 收集依赖信息
        val dependencies = injectFields.map { field ->
            InjectParameter(field.requiredType, field.nameQualifier, field.lazy)
        } + injectMethods.flatMap { it.parameters }
        
        return BeanDefinition(
            name = name,
            type = clazz,
            constructor = null, // 手动注册的实例已经创建，不需要构造函数
            injectFields = injectFields,
            injectMethods = injectMethods,
            postConstruct = postConstructMethods.firstOrNull(),
            postEnable = postEnableMethods.firstOrNull(),
            preDestroy = preDestroyMethods.firstOrNull(),
            constructorParameters = emptyList(),
            dependencies = dependencies,
            lazyInit = false,
            scope = BeanScopes.SINGLETON,
            isAspect = false,
            isPrimary = false,
            order = Int.MAX_VALUE,
            valueFields = valueFields,
            factoryBeanName = null,
            factoryMethod = null,
            dependsOn = emptyList(),
            postConstructMethods = postConstructMethods,
            postEnableMethods = postEnableMethods,
            preDestroyMethods = preDestroyMethods
        )
    }

    fun registerScope(name: String, scope: BeanScope) {
        val normalized = BeanScopes.normalize(name)
        require(!BeanScopes.isStandard(normalized)) { "标准作用域不允许被覆盖: $name" }
        customScopes[normalized] = scope
        debug("[IoC] 注册自定义作用域: $normalized")
    }
    /**
     * 刷新 refresh 作用域中的 Bean。
     *
     * @param name Bean 名称，为 null 时刷新全部
     */
    fun refreshScope(name: String? = null) {
        val scope = customScopes[BeanScopes.REFRESH] as? RefreshBeanScope
            ?: return
        scope.refresh(name)
        debug("[IoC] 刷新 refresh 作用域: ${name ?: "全部"}")
    }

    /**
     * 获取线程作用域实例（用于手动清理当前线程缓存）。
     */
    fun getThreadScope(): ThreadBeanScope? {
        return customScopes[BeanScopes.THREAD] as? ThreadBeanScope
    }

    /**
     * 获取容器事件总线，用于监听 Bean 生命周期事件。
     */
    fun getEventBus(): EventBus = lifecycleManager.eventBus


    /**
     * 为 object 类的 @Lazy 字段注入延迟代理。
     * 供 ObjectInjector 调用，委托给内部的 FieldInjector。
     */
    internal fun injectLazyObjectField(instance: Any, type: Class<*>, nameQualifier: String?, field: java.lang.reflect.Field) {
        fieldInjector.injectLazyField(instance, type, nameQualifier, field)
    }

    internal fun initialize() {
        if (initialized) return

        synchronized(this) {
            if (initialized) return

            registerBuiltinScopes()

            val start = System.nanoTime()
            debug("[IoC] 开始初始化容器，共 ${registry.getAll().size} 个 Bean 定义")

            initializing = true
            try {
                // 先初始化切面 Bean 并解析 Advisor
                initializeAspects()
                // 发现并注册 BeanPostProcessor
                discoverBeanPostProcessors()
                // 再初始化所有 Bean（切面 Bean 已缓存，不会重复创建）
                lifecycleManager.initialize()
                initialized = true
            } finally {
                initializing = false
            }

            val ms = (System.nanoTime() - start) / 1_000_000.0
            debug("[IoC] BeanContainer 初始化完成，总耗时 ${"%.2f".format(ms)}ms")
        }
    }

    /**
     * 初始化切面 Bean 并解析 Advisor。
     */
    private fun initializeAspects() {
        val aspectDefinitions = registry.getAll().filter { it.isAspect }
        if (aspectDefinitions.isEmpty()) return

        debug("[IoC] 发现 ${aspectDefinitions.size} 个切面，开始解析")
        for (definition in aspectDefinitions) {
            val aspectInstance = lifecycleManager.getOrCreateSingleton(definition)
            val advisors = AspectScanner.scan(aspectInstance, definition.type)
            advisorRegistry.registerAll(advisors)
            debug("[IoC] 切面 ${definition.name} 解析完成，共 ${advisors.size} 个通知器")
        }
        debug("[IoC] 切面解析完成，共 ${advisorRegistry.getAll().size} 个通知器")
    }

    /**
     * 发现并注册 BeanPostProcessor。
     */
    private fun discoverBeanPostProcessors() {
        val processorDefinitions = registry.getAll().filter {
            BeanPostProcessor::class.java.isAssignableFrom(it.type)
        }
        if (processorDefinitions.isEmpty()) return

        debug("[IoC] 发现 ${processorDefinitions.size} 个 BeanPostProcessor，开始注册")
        for (definition in processorDefinitions) {
            val processor = lifecycleManager.getOrCreateSingleton(definition)
            if (processor is BeanPostProcessor) {
                lifecycleManager.addBeanPostProcessor(processor)
                debug("[IoC] 注册 BeanPostProcessor: ${definition.name}")
            }
        }
    }

    internal fun invokePostEnable() {
        if (!initialized) return
        lifecycleManager.invokePostEnable()
    }

    internal fun shutdown() {
        if (!initialized) return

        val start = System.nanoTime()
        lifecycleManager.shutdown()
        clearScopes()
        advisorRegistry.clear()
        cycleResolver.clear()
        registry.clear()
        manualBeansByName.clear()
        lifecycleManager.eventBus.clear()
        ValueResolver.clearProperties()
        initialized = false

        val ms = (System.nanoTime() - start) / 1_000_000.0
        debug("[IoC] 容器已关闭，总耗时 ${"%.2f".format(ms)}ms")
    }

    internal fun getRegistry(): BeanRegistry = registry

    internal fun getScanner(): ClassScanner = scanner

    internal fun createConditionContext(): ConditionContext {
        return object : ConditionContext {
            override fun getClassLoader(): ClassLoader =
                Thread.currentThread().contextClassLoader ?: BeanContainer::class.java.classLoader

            override fun containsBeanDefinition(name: String): Boolean =
                registry.contains(name)

            override fun getBeanNamesForType(type: Class<*>): List<String> =
                registry.getByType(type).map { it.name }
        }
    }

    internal fun resetForTesting() {
        lifecycleManager.resetState()
        clearScopes()
        advisorRegistry.clear()
        cycleResolver.clear()
        registry.clear()
        manualBeansByName.clear()
        ValueResolver.clearProperties()
        initialized = false
        initializing = false
    }

    private fun registerBuiltinScopes() {
        if (!customScopes.containsKey(BeanScopes.THREAD)) {
            customScopes[BeanScopes.THREAD] = ThreadBeanScope()
        }
        if (!customScopes.containsKey(BeanScopes.REFRESH)) {
            customScopes[BeanScopes.REFRESH] = RefreshBeanScope(registry)
        }
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


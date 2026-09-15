package top.wcpe.taboolib.ioc.bean

import taboolib.common.platform.function.debug
import taboolib.common.platform.function.warning
import top.wcpe.taboolib.ioc.annotation.Aspect
import top.wcpe.taboolib.ioc.annotation.ConditionContext
import top.wcpe.taboolib.ioc.aop.AdvisorRegistry
import top.wcpe.taboolib.ioc.aop.AopProxyFactory
import top.wcpe.taboolib.ioc.aop.AopWeavingRuntime
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

    /**
     * 容器初始化前通过 [registerBean] 注册的 Bean 队列（保持注册顺序）。
     * 初始化时会按序重放完整生命周期（注入 / BeanPostProcessor / @PostConstruct / AOP / @PreDestroy 登记），
     * 修复「初始化前注册的 Bean 永远拿不到注入与生命周期回调」的静默缺陷。
     */
    private val pendingManualBeans = LinkedHashMap<String, Any>()

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
        flushPendingManualBeansIfNeeded()
        return resolver.getBean(type, name)
    }

    /**
     * 获取指定类型的所有 Bean。
     *
     * **守卫语义（与 [getBean] 对齐，A-P1-04）**：容器未初始化且未处于初始化中时返回空列表；
     * 在 `initializing` 期间（`initializePendingManualBeans` 重放阶段）**允许**枚举，
     * 以便重放中的手动 Bean 能通过类型解析其依赖（与 [getBean] 的 `!initialized && !initializing` 守卫一致）。
     */
    fun <T> getBeansOfType(type: Class<T>): List<T> {
        if (!initialized && !initializing) return emptyList()
        flushPendingManualBeansIfNeeded()
        return resolver.getBeansOfType(type)
    }

    fun containsBean(name: String): Boolean {
        flushPendingManualBeansIfNeeded()
        return resolver.containsBean(name)
    }

    fun getBeanNames(): Set<String> {
        flushPendingManualBeansIfNeeded()
        return resolver.getBeanNames()
    }

    /**
     * 惰性补全：若容器已可用（`initialized` 或 `initializing`）且仍有排队中的
     * 初始化前手动 Bean，则按注册顺序补全其完整生命周期后再暴露。
     *
     * 这是「延迟暴露」（A-P0-02）与「注册后即可使用」之间的桥梁：
     * - 真正常规路径下，[initialize] 会在创建扫描 Bean 之前清空队列；
     * - 若外部代码（或测试）在未调用 [initialize] 的情况下直接置位 `initialized` 并查询，
     *   这里会**先补全生命周期再暴露**，绝不会暴露未初始化的裸实例。
     */
    private fun flushPendingManualBeansIfNeeded() {
        if (!initialized && !initializing) return
        if (pendingManualBeans.isEmpty()) return
        initializePendingManualBeans()
    }

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
     * 若在容器初始化（ENABLE）之前调用，实例会先进入 [pendingManualBeans] 队列，
     * [initialize] 时按注册顺序重放上述完整生命周期。
     *
     * **延迟暴露（A-P0-02）**：初始化前注册的实例**不会**被提前写入
     * `manualBeansByName` / `cycleResolver`。因为初始化前 `getBean` 本就返回 null
     * （见 [getBean] 的守卫），提前暴露对「初始化前解析」没有任何收益，却会让
     * 未注入、未执行 @PostConstruct 的**裸半成品**在 `initializing` 窗口内被
     * 依赖方命中（静默注入半成品）。因此统一改为「初始化时按注册顺序补全后再暴露」。
     *
     * **注册顺序约束**：重放时若 A 先于其依赖的 B 注册，则补全 A 时 B 尚未暴露，
     * 会**显式失败**（而非静默注入 B 的裸实例）—— 这是确定性的、可诊断的行为。
     * 需要 A 依赖 B 时，请先 `registerBean` B 再 `registerBean` A。
     *
     * @param name Bean 名称
     * @param instance Bean 实例
     */
    fun registerBean(name: String, instance: Any) {
        if (!initialized && !initializing) {
            // 容器未初始化：仅入队，不提前暴露（延迟暴露，消除半成品可见窗口）
            synchronized(pendingManualBeans) { pendingManualBeans[name] = instance }
            debug("[IoC] 手动注册 Bean（已排队，初始化时补全生命周期）: $name")
            return
        }
        registerBeanWithFullLifecycle(name, instance)
    }

    /**
     * 阶段 1：把初始化前注册的手动 Bean 的 **BeanDefinition** 登记进注册表（不执行生命周期）。
     *
     * 必须在 `initializeAspects()` / `discoverBeanPostProcessors()` 之前调用，
     * 否则手动注册的 @Aspect / BeanPostProcessor 在发现阶段不可见（A-P0-01）。
     *
     * 已缓存的实例（例如被更早解析命中）会被跳过，避免重复登记。
     */
    private fun registerPendingManualBeanDefinitions() {
        val pending = synchronized(pendingManualBeans) {
            val copy = pendingManualBeans.toList()
            copy
        }
        if (pending.isEmpty()) return
        for ((name, instance) in pending) {
            if (registry.contains(name)) continue
            registry.register(createManualBeanDefinition(name, instance))
        }
        debug("[IoC] 已登记 ${pending.size} 个初始化前注册 Bean 的定义: ${pending.joinToString(", ") { it.first }}")
    }

    /**
     * 阶段 2：补全初始化前注册 Bean 的完整生命周期（注入 / BPP / @PostConstruct / AOP）。
     *
     * 必须在 `discoverBeanPostProcessors()` 之后、`lifecycleManager.initialize()` 之前执行，
     * 使扫描 Bean 的注入能命中已补全生命周期的手动 Bean。
     */
    private fun initializePendingManualBeans() {
        val pending = synchronized(pendingManualBeans) {
            val copy = pendingManualBeans.toList()
            pendingManualBeans.clear()
            copy
        }
        if (pending.isEmpty()) return
        debug("[IoC] 开始补全 ${pending.size} 个初始化前注册 Bean 的生命周期: ${pending.joinToString(", ") { it.first }}")
        for ((name, instance) in pending) {
            registerBeanWithFullLifecycle(name, instance)
        }
    }

    /**
     * 执行完整生命周期并缓存：注入 → BeanPostProcessor → @PostConstruct → AOP 包装 → 缓存。
     *
     * 实例化已由调用方完成（手动注册），因此委托给
     * [LifecycleManager.registerExistingSingleton]，**同样进入环检测门**
     * （A-P1-03），使手动 Bean 之间的循环依赖能被显式检测。
     */
    private fun registerBeanWithFullLifecycle(name: String, instance: Any) {
        // 定义可能已在阶段 1 登记；若未登记（初始化后调用 registerBean）则补登记
        val definition = registry.getByName(name) ?: createManualBeanDefinition(name, instance)
        if (!registry.contains(name)) {
            registry.register(definition)
        }

        try {
            // 交给 LifecycleManager 执行注入 / BPP / @PostConstruct / AOP，并缓存到 cycleResolver
            lifecycleManager.registerExistingSingleton(definition, instance)

            // 缓存到手动 Bean 名称索引
            val finalInstance = cycleResolver.getSingleton(name) ?: instance
            manualBeansByName[name] = finalInstance

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
            // A-P0-01：手动注册路径必须与扫描路径同源判定 @Aspect，
            // 否则手动注册的切面其 Advisor 永远不被注册（通知静默失效），
            // 且会被自我代理（宽切点下递归 → StackOverflowError）。
            // 判据与 scan/ClassScanner.kt:22 逐字一致：clazz.isAnnotationPresent(Aspect::class.java)。
            isAspect = clazz.isAnnotationPresent(Aspect::class.java),
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
                // 1) 先把初始化前注册的 Bean 定义登记进注册表（**不**执行生命周期）。
                //    必须在 initializeAspects / discoverBeanPostProcessors 之前，
                //    否则手动注册的 @Aspect（isAspect=true）与 BeanPostProcessor
                //    在发现阶段不可见 → 切面通知静默失效（A-P0-01）。
                registerPendingManualBeanDefinitions()
                // 2) 初始化切面 Bean 并解析 Advisor（此时手动切面已在注册表中）
                initializeAspects()
                // 编译期织入的方法体通过这个入口回调容器（挂载点必须在切面注册完成之后）
                AopWeavingRuntime.attach(advisorRegistry)
                // 3) 发现并注册 BeanPostProcessor（此时手动 BPP 已在注册表中）
                discoverBeanPostProcessors()
                // 4) 补全初始化前注册 Bean 的完整生命周期（注入 / BPP / @PostConstruct / AOP），
                //    必须在 lifecycleManager.initialize() 之前，使扫描 Bean 的注入能命中这些手动 Bean。
                initializePendingManualBeans()
                // 5) 再初始化所有 Bean（切面 Bean 已缓存，不会重复创建）
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
     * 解析一个 Bean 定义对应的实例，供切面 / BeanPostProcessor 发现阶段使用。
     *
     * - 若该定义对应一个**初始化前手动注册**的实例（定义由 [registerPendingManualBeanDefinitions]
     *   登记、实例仍在 [pendingManualBeans] 中），则先补全其完整生命周期并返回同一实例，
     *   避免 `createBean` 因 `constructor == null` 而失败（A-P0-01）。
     * - 否则走正常的 `getOrCreateSingleton` 创建流程。
     */
    private fun resolveExistingOrCreate(definition: BeanDefinition): Any {
        val manualInstance = synchronized(pendingManualBeans) { pendingManualBeans[definition.name] }
        if (manualInstance != null) {
            // 复用「手动注册 Bean 完整生命周期」路径（含环检测门），并返回最终实例
            registerBeanWithFullLifecycle(definition.name, manualInstance)
            return cycleResolver.getSingleton(definition.name) ?: manualInstance
        }
        return lifecycleManager.getOrCreateSingleton(definition)
    }

    /**
     * 初始化切面 Bean 并解析 Advisor。
     */
    private fun initializeAspects() {
        val aspectDefinitions = registry.getAll().filter { it.isAspect }
        if (aspectDefinitions.isEmpty()) return

        debug("[IoC] 发现 ${aspectDefinitions.size} 个切面，开始解析")
        for (definition in aspectDefinitions) {
            val aspectInstance = resolveExistingOrCreate(definition)
            val advisors = AspectScanner.scan(aspectInstance, definition.type)
            advisorRegistry.registerAll(advisors)
            debug("[IoC] 切面 ${definition.name} 解析完成，共 ${advisors.size} 个通知器")
        }
        debug("[IoC] 切面解析完成，共 ${advisorRegistry.getAll().size} 个通知器")
    }

    /**
     * 发现并注册用户自定义的 BeanPostProcessor。
     *
     * 内置处理器（`@WrapWith` 装饰器支持）由 [LifecycleManager] 自身持有，不在这里注册。
     */
    private fun discoverBeanPostProcessors() {
        val processorDefinitions = registry.getAll().filter {
            BeanPostProcessor::class.java.isAssignableFrom(it.type)
        }
        if (processorDefinitions.isEmpty()) return

        debug("[IoC] 发现 ${processorDefinitions.size} 个 BeanPostProcessor，开始注册")
        for (definition in processorDefinitions) {
            val processor = resolveExistingOrCreate(definition)
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
        AopWeavingRuntime.detach()
        cycleResolver.clear()
        registry.clear()
        manualBeansByName.clear()
        synchronized(pendingManualBeans) { pendingManualBeans.clear() }
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
        AopWeavingRuntime.detach()
        cycleResolver.clear()
        registry.clear()
        manualBeansByName.clear()
        synchronized(pendingManualBeans) { pendingManualBeans.clear() }
        // A-P1-05：与 shutdown 语义一致地清理事件总线，避免监听器跨 reset 周期累积泄漏
        lifecycleManager.eventBus.clear()
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
            runCatching {
                scope.clear()
                // ThreadBeanScope 需要跨线程清理：clear() 只能清当前线程，
                // 池化线程持有的缓存实例必须在此一次性断开引用，
                // 否则插件重载后旧 ClassLoader 无法回收
                if (scope is ThreadBeanScope) scope.clearAllThreads()
            }.onFailure { warning("[IoC] 清理自定义作用域失败: ${it.message}") }
        }
        customScopes.clear()
    }

    private fun getScope(name: String): BeanScope? {
        return customScopes[BeanScopes.normalize(name)]
    }
}


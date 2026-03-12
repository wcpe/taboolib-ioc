package top.wcpe.ioc.example

import top.wcpe.taboolib.ioc.annotation.ConditionContext
import top.wcpe.taboolib.ioc.aop.AdvisorRegistry
import top.wcpe.taboolib.ioc.aop.AopProxyFactory
import top.wcpe.taboolib.ioc.aop.AspectScanner
import top.wcpe.taboolib.ioc.bean.BeanDefinition
import top.wcpe.taboolib.ioc.bean.BeanResolver
import top.wcpe.taboolib.ioc.bean.BeanRegistry
import top.wcpe.taboolib.ioc.bean.BeanScope
import top.wcpe.taboolib.ioc.condition.ConditionEvaluator
import top.wcpe.taboolib.ioc.cycle.CycleDetector
import top.wcpe.taboolib.ioc.cycle.CycleResolver
import top.wcpe.taboolib.ioc.inject.ConstructorResolver
import top.wcpe.taboolib.ioc.inject.FieldInjector
import top.wcpe.taboolib.ioc.inject.Injector
import top.wcpe.taboolib.ioc.lifecycle.LifecycleManager
import top.wcpe.taboolib.ioc.scan.ClassScanner
import java.util.concurrent.ConcurrentHashMap

/**
 * 轻量级 IoC 测试上下文。
 *
 * 不依赖 TabooLib 运行时和 BeanContainer 单例，
 * 可在纯 JUnit 环境中独立运行，方便编写单元测试。
 */
class IocTestContext {

    val registry = BeanRegistry()
    private val cycleResolver = CycleResolver()
    private val cycleDetector = CycleDetector()
    private val constructorResolver = ConstructorResolver()
    private val manualBeans = ConcurrentHashMap<String, Any>()
    private val customScopes = ConcurrentHashMap<String, BeanScope>()
    private val fieldInjector = FieldInjector(registry) { type, name -> beanLookup(type, name) }
    private val injector = Injector(fieldInjector) { type, name -> beanLookup(type, name) }
    val advisorRegistry = AdvisorRegistry()
    private val aopProxyFactory = AopProxyFactory(advisorRegistry)
    val lifecycleManager = LifecycleManager(
        registry, cycleResolver, injector, cycleDetector,
        { name -> customScopes[name] },
        aopProxyFactory
    )
    private val scanner = ClassScanner(constructorResolver)
    private val resolver = BeanResolver(
        registry = registry,
        manualBeans = manualBeans,
        singletonProvider = lifecycleManager::getOrCreateSingleton,
        transientProvider = lifecycleManager::createTransient,
        scopeLookup = { name -> customScopes[name] }
    )

    private fun beanLookup(type: Class<*>, name: String?): Any? = resolver.getBean(type, name)

    /** 扫描并注册一个组件类 */
    fun register(clazz: Class<*>) {
        val definition = scanner.scan(clazz) ?: error("扫描失败: ${clazz.name}")
        registry.register(definition)
    }

    /**
     * 带条件评估的注册。
     * 返回 true 表示注册成功，false 表示条件不满足被跳过。
     */
    fun registerWithCondition(clazz: Class<*>): Boolean {
        val definition = scanner.scan(clazz) ?: return false
        val context = createConditionContext()
        if (ConditionEvaluator.shouldSkipOnScan(clazz, context)) return false
        if (ConditionEvaluator.shouldSkipOnBeanCondition(clazz, context)) return false
        registry.register(definition)
        return true
    }

    /** 创建条件评估上下文 */
    fun createConditionContext(): ConditionContext {
        return object : ConditionContext {
            override fun getClassLoader(): ClassLoader =
                Thread.currentThread().contextClassLoader ?: IocTestContext::class.java.classLoader

            override fun containsBeanDefinition(name: String): Boolean =
                registry.contains(name)

            override fun getBeanNamesForType(type: Class<*>): List<String> =
                registry.getByType(type).map { it.name }
        }
    }

    /** 手动注册一个 Bean 实例 */
    fun registerBean(name: String, instance: Any) {
        manualBeans[name] = instance
        cycleResolver.addSingleton(name, instance)
    }

    /** 注册自定义作用域 */
    fun registerScope(name: String, scope: BeanScope) {
        customScopes[name] = scope
    }

    /** 初始化容器（预创建 eager singleton） */
    fun initialize() {
        // 先初始化切面 Bean 并解析 Advisor
        val aspectDefinitions = registry.getAll().filter { it.isAspect }
        for (definition in aspectDefinitions) {
            val aspectInstance = lifecycleManager.getOrCreateSingleton(definition)
            val advisors = AspectScanner.scan(aspectInstance, definition.type)
            advisorRegistry.registerAll(advisors)
        }
        lifecycleManager.initialize()
    }

    /** 执行所有已初始化 singleton Bean 的 @PostEnable 方法 */
    fun invokePostEnable() {
        lifecycleManager.invokePostEnable()
    }

    /** 按类型获取 Bean */
    fun <T> getBean(type: Class<T>, name: String? = null): T? = resolver.getBean(type, name)

    /** 获取某类型的所有 Bean */
    fun <T> getBeansOfType(type: Class<T>): List<T> = resolver.getBeansOfType(type)

    /** 检查 Bean 是否存在 */
    fun containsBean(name: String): Boolean = resolver.containsBean(name)

    /** 获取所有 Bean 名称 */
    fun getBeanNames(): Set<String> = resolver.getBeanNames()

    /** 获取已创建的 singleton 实例 */
    fun getSingleton(name: String): Any? = cycleResolver.getSingleton(name)
}

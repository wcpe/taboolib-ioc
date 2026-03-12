package top.wcpe.ioc.example

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import top.wcpe.taboolib.ioc.annotation.*
import top.wcpe.taboolib.ioc.bean.BeanDefinition
import top.wcpe.taboolib.ioc.bean.BeanScope
import top.wcpe.taboolib.ioc.cycle.CircularDependencyException
import java.util.concurrent.ConcurrentHashMap

/**
 * taboolib-ioc 示例插件单元测试。
 *
 * 展示如何使用 [IocTestContext] 脱离 TabooLib 运行时，
 * 在纯 JUnit 环境中对 IoC 管理的组件进行单元测试。
 *
 * 每个测试方法创建独立的 [IocTestContext]，互不干扰。
 */
class ExamplePluginIoCTest {

    // ==================== 1. 构造函数注入 ====================

    @Test
    fun `构造函数注入 - Service 通过构造函数获取 Repository 依赖`() {
        val ctx = IocTestContext()
        ctx.register(SimpleUserRepository::class.java)
        ctx.register(SimpleUserService::class.java)
        ctx.initialize()

        val service = ctx.getBean(SimpleUserService::class.java)

        assertNotNull(service)
        assertEquals("user-alice", service!!.findUser("alice"))
    }

    // ==================== 2. 字段注入 ====================

    @Test
    fun `字段注入 - 使用 @Inject 标记 lateinit var 自动注入依赖`() {
        val ctx = IocTestContext()
        ctx.register(SimpleUserRepository::class.java)
        ctx.register(FieldInjectedService::class.java)
        ctx.initialize()

        val service = ctx.getBean(FieldInjectedService::class.java)

        assertNotNull(service)
        assertEquals("user-bob", service!!.findUser("bob"))
    }

    // ==================== 3. 方法注入 ====================

    @Test
    fun `方法注入 - 使用 @Inject 标记方法自动注入参数`() {
        val ctx = IocTestContext()
        ctx.register(SimpleUserRepository::class.java)
        ctx.register(MethodInjectedService::class.java)
        ctx.initialize()

        val service = ctx.getBean(MethodInjectedService::class.java)

        assertNotNull(service)
        assertEquals("user-charlie", service!!.findUser("charlie"))
    }

    // ==================== 4. @Named 名称限定注入 ====================

    @Test
    fun `Named限定注入 - 同一接口多个实现时按名称选择`() {
        val ctx = IocTestContext()
        ctx.register(JsonSerializer::class.java)
        ctx.register(XmlSerializer::class.java)
        ctx.register(NamedConsumer::class.java)
        ctx.initialize()

        val consumer = ctx.getBean(NamedConsumer::class.java)

        assertNotNull(consumer)
        assertEquals("json", consumer!!.primaryFormat())
        assertEquals("xml", consumer.secondaryFormat())
    }

    // ==================== 5. @Resource 方法注入限定 ====================

    @Test
    fun `Resource限定注入 - 通过 @Resource(name) 指定方法注入的实现`() {
        val ctx = IocTestContext()
        ctx.register(JsonSerializer::class.java)
        ctx.register(XmlSerializer::class.java)
        ctx.register(ResourceConsumer::class.java)
        ctx.initialize()

        val consumer = ctx.getBean(ResourceConsumer::class.java)

        assertNotNull(consumer)
        assertEquals("xml", consumer!!.format())
    }

    // ==================== 6. @PostConstruct 生命周期回调 ====================

    @Test
    fun `PostConstruct - Bean 创建并注入完成后自动调用初始化方法`() {
        PostConstructService.initialized = false
        PostConstructService.repoAvailable = false

        val ctx = IocTestContext()
        ctx.register(SimpleUserRepository::class.java)
        ctx.register(PostConstructService::class.java)
        ctx.initialize()

        assertTrue(PostConstructService.initialized, "@PostConstruct 应被调用")
        assertTrue(PostConstructService.repoAvailable, "@PostConstruct 执行时依赖应已注入")
    }

    // ==================== 7. @PreDestroy 销毁回调 ====================

    @Test
    fun `PreDestroy - 容器关闭时调用销毁方法`() {
        DestroyableService.destroyed = false

        val ctx = IocTestContext()
        ctx.register(DestroyableService::class.java)
        ctx.initialize()

        assertFalse(DestroyableService.destroyed, "初始化后不应触发 @PreDestroy")

        ctx.lifecycleManager.shutdown()

        assertTrue(DestroyableService.destroyed, "shutdown 后应触发 @PreDestroy")
    }

    // ==================== 8. Prototype 作用域 ====================

    @Test
    fun `Prototype作用域 - 每次获取都创建新实例`() {
        PrototypeCounter.count = 0

        val ctx = IocTestContext()
        ctx.register(PrototypeCounter::class.java)
        ctx.initialize()

        assertEquals(0, PrototypeCounter.count, "Prototype 初始化阶段不应创建实例")

        val first = ctx.getBean(PrototypeCounter::class.java)
        val second = ctx.getBean(PrototypeCounter::class.java)
        val third = ctx.getBean(PrototypeCounter::class.java)

        assertNotNull(first)
        assertNotNull(second)
        assertNotNull(third)
        assertNotSame(first, second)
        assertNotSame(second, third)
        assertEquals(3, PrototypeCounter.count)
        assertEquals(1, first!!.id)
        assertEquals(2, second!!.id)
        assertEquals(3, third!!.id)
    }

    // ==================== 9. @Lazy 延迟初始化 ====================

    @Test
    fun `Lazy延迟初始化 - 首次获取时才创建实例且为单例`() {
        LazyService.constructed = 0

        val ctx = IocTestContext()
        ctx.register(LazyService::class.java)
        ctx.initialize()

        assertEquals(0, LazyService.constructed, "Lazy Bean 在 initialize 阶段不应被创建")

        val first = ctx.getBean(LazyService::class.java)
        assertEquals(1, LazyService.constructed)

        val second = ctx.getBean(LazyService::class.java)
        assertEquals(1, LazyService.constructed, "Lazy singleton 第二次获取不应再创建")
        assertSame(first, second)
    }

    // ==================== 10. 自定义 Scope ====================

    @Test
    fun `自定义Scope - 注册自定义作用域控制 Bean 生命周期`() {
        SessionScopedBean.constructed = 0
        val sessionCache = ConcurrentHashMap<String, Any>()

        val ctx = IocTestContext()
        ctx.register(SessionScopedBean::class.java)
        ctx.registerScope("session", object : BeanScope {
            override fun get(name: String, definition: BeanDefinition, creator: () -> Any): Any {
                return sessionCache.getOrPut(name) { creator() }
            }

            override fun clear() {
                sessionCache.clear()
            }
        })
        ctx.initialize()

        assertEquals(0, SessionScopedBean.constructed)

        val first = ctx.getBean(SessionScopedBean::class.java)
        val second = ctx.getBean(SessionScopedBean::class.java)

        assertNotNull(first)
        assertSame(first, second, "同一 session 内应返回相同实例")
        assertEquals(1, SessionScopedBean.constructed)
    }

    // ==================== 11. 手动注册 Bean ====================

    @Test
    fun `手动注册Bean - registerBean 注册的实例可被容器查询和注入`() {
        val ctx = IocTestContext()
        ctx.register(ManualBeanConsumer::class.java)
        ctx.registerBean("appConfig", AppConfig(env = "test", debug = true))
        ctx.initialize()

        val config = ctx.getBean(AppConfig::class.java, "appConfig")
        assertNotNull(config)
        assertEquals("test", config!!.env)
        assertTrue(config.debug)
        assertTrue(ctx.containsBean("appConfig"))
    }

    // ==================== 12. 接口类型解析 ====================

    @Test
    fun `接口类型解析 - 通过接口类型获取具体实现`() {
        val ctx = IocTestContext()
        ctx.register(JsonSerializer::class.java)
        ctx.initialize()

        val byInterface = ctx.getBean(Serializer::class.java)
        assertNotNull(byInterface)
        assertEquals("json", byInterface!!.format())

        val byImpl = ctx.getBean(JsonSerializer::class.java)
        assertSame(byInterface, byImpl, "接口和实现类应解析到同一实例")
    }

    // ==================== 13. getBeansOfType 聚合查询 ====================

    @Test
    fun `getBeansOfType - 获取某接口的所有实现`() {
        val ctx = IocTestContext()
        ctx.register(JsonSerializer::class.java)
        ctx.register(XmlSerializer::class.java)
        ctx.initialize()

        val all = ctx.getBeansOfType(Serializer::class.java)
        val formats = all.map { it.format() }.sorted()

        assertEquals(2, all.size)
        assertEquals(listOf("json", "xml"), formats)
    }

    // ==================== 14. 字段循环依赖解析 ====================

    @Test
    fun `字段循环依赖 - singleton Bean 的字段循环依赖可正常解析`() {
        val ctx = IocTestContext()
        ctx.register(CycleNodeA::class.java)
        ctx.register(CycleNodeB::class.java)
        ctx.initialize()

        val a = ctx.getBean(CycleNodeA::class.java)
        val b = ctx.getBean(CycleNodeB::class.java)

        assertNotNull(a)
        assertNotNull(b)
        assertSame(b, a!!.nodeB, "A 的字段应指向 B")
        assertSame(a, b!!.nodeA, "B 的字段应指向 A")
        assertEquals("A->B", a.describe())
        assertEquals("B->A", b.describe())
    }

    // ==================== 15. 构造函数循环依赖拒绝 ====================

    @Test
    fun `构造函数循环依赖 - 构造函数循环依赖应抛出异常并包含依赖链`() {
        val ctx = IocTestContext()
        ctx.register(CtorCycleX::class.java)
        ctx.register(CtorCycleY::class.java)

        val exception = assertThrows(CircularDependencyException::class.java) {
            ctx.initialize()
        }

        assertTrue(exception.dependencyChain.size >= 3, "依赖链应至少包含 3 个节点")
        assertTrue(
            exception.dependencyChain.first() == exception.dependencyChain.last(),
            "依赖链首尾应相同，形成环"
        )
    }

    // ==================== 16. @PostEnable 生命周期回调 ====================

    @Test
    fun `PostEnable - 在 invokePostEnable 后执行且依赖已注入`() {
        PostEnableService.enabled = false
        PostEnableService.repoAvailable = false

        val ctx = IocTestContext()
        ctx.register(SimpleUserRepository::class.java)
        ctx.register(PostEnableService::class.java)
        ctx.initialize()

        assertFalse(PostEnableService.enabled, "@PostEnable 不应在 initialize 时执行")

        ctx.invokePostEnable()

        assertTrue(PostEnableService.enabled, "@PostEnable 应在 invokePostEnable 后执行")
        assertTrue(PostEnableService.repoAvailable, "@PostEnable 执行时依赖应已注入")
    }

    // ==================== 17. @PostEnable 执行顺序 ====================

    @Test
    fun `PostEnable执行顺序 - PostConstruct 在 PostEnable 之前执行`() {
        LifecycleOrderService.order.clear()

        val ctx = IocTestContext()
        ctx.register(LifecycleOrderService::class.java)
        ctx.initialize()
        ctx.invokePostEnable()

        assertEquals(listOf("PostConstruct", "PostEnable"), LifecycleOrderService.order)
    }
}

// ==================== 测试用 Bean 定义 ====================

// --- 基础组件 ---

@Repository
class SimpleUserRepository {
    fun findUser(id: String): String = "user-$id"
}

@Service
class SimpleUserService @Inject constructor(
    private val repo: SimpleUserRepository
) {
    fun findUser(id: String): String = repo.findUser(id)
}

// --- 字段注入 ---

@Service
class FieldInjectedService {
    @Inject
    lateinit var repo: SimpleUserRepository

    fun findUser(id: String): String = repo.findUser(id)
}

// --- 方法注入 ---

@Service
class MethodInjectedService {
    private lateinit var repo: SimpleUserRepository

    @Inject
    fun bindRepo(repo: SimpleUserRepository) {
        this.repo = repo
    }

    fun findUser(id: String): String = repo.findUser(id)
}

// --- 接口 + Named ---

interface Serializer {
    fun format(): String
}

@Component("jsonSerializer")
class JsonSerializer : Serializer {
    override fun format(): String = "json"
}

@Component("xmlSerializer")
class XmlSerializer : Serializer {
    override fun format(): String = "xml"
}

@Service
class NamedConsumer {
    @Inject
    @Named("jsonSerializer")
    lateinit var primary: Serializer

    @Inject
    @Named("xmlSerializer")
    lateinit var secondary: Serializer

    fun primaryFormat(): String = primary.format()
    fun secondaryFormat(): String = secondary.format()
}

// --- @Resource ---

@Service
class ResourceConsumer {
    private lateinit var serializer: Serializer

    @Resource(name = "xmlSerializer")
    fun bindSerializer(s: Serializer) {
        this.serializer = s
    }

    fun format(): String = serializer.format()
}

// --- @PostConstruct ---

@Service
class PostConstructService {
    companion object {
        var initialized = false
        var repoAvailable = false
    }

    @Inject
    lateinit var repo: SimpleUserRepository

    @PostConstruct
    fun onInit() {
        initialized = true
        repoAvailable = this::repo.isInitialized
    }
}

// --- @PreDestroy ---

@Component
class DestroyableService {
    companion object {
        var destroyed = false
    }

    @PreDestroy
    fun onDestroy() {
        destroyed = true
    }
}

// --- Prototype ---

@Component
@Prototype
class PrototypeCounter {
    companion object {
        var count = 0
    }

    val id: Int = ++count
}

// --- Lazy ---

@Component
@Lazy
class LazyService {
    companion object {
        var constructed = 0
    }

    init {
        constructed++
    }
}

// --- 自定义 Scope ---

@Component
@Scope("session")
class SessionScopedBean {
    companion object {
        var constructed = 0
    }

    init {
        constructed++
    }
}

// --- 手动注册 ---

data class AppConfig(val env: String, val debug: Boolean)

@Service
class ManualBeanConsumer

// --- 字段循环依赖 ---

@Component
class CycleNodeA {
    @Inject
    lateinit var nodeB: CycleNodeB

    fun describe(): String = "A->${nodeB.label()}"
    fun label(): String = "A"
}

@Component
class CycleNodeB {
    @Inject
    lateinit var nodeA: CycleNodeA

    fun describe(): String = "B->${nodeA.label()}"
    fun label(): String = "B"
}

// --- 构造函数循环依赖 ---

@Component
class CtorCycleX @Inject constructor(val y: CtorCycleY)

@Component
class CtorCycleY @Inject constructor(val x: CtorCycleX)

// --- @PostEnable ---

@Service
class PostEnableService {
    companion object {
        var enabled = false
        var repoAvailable = false
    }

    @Inject
    lateinit var repo: SimpleUserRepository

    @PostEnable
    fun onEnable() {
        enabled = true
        repoAvailable = this::repo.isInitialized
    }
}

// --- 生命周期顺序 ---

@Service
class LifecycleOrderService {
    companion object {
        val order = mutableListOf<String>()
    }

    @PostConstruct
    fun onInit() {
        order.add("PostConstruct")
    }

    @PostEnable
    fun onEnable() {
        order.add("PostEnable")
    }
}

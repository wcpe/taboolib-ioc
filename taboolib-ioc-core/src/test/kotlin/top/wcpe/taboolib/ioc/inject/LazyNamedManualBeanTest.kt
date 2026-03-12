package top.wcpe.taboolib.ioc.inject

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import top.wcpe.taboolib.ioc.IocTestContext
import top.wcpe.taboolib.ioc.annotation.Component
import top.wcpe.taboolib.ioc.annotation.Inject
import top.wcpe.taboolib.ioc.annotation.Lazy
import top.wcpe.taboolib.ioc.annotation.Named

/**
 * 测试 @Lazy + @Named 注入手动注册的 Bean（registerBean）场景
 *
 * serverName 相关测试采用 Provider 模式：
 * 运行期才能确定的值（如 serverName）不应作为容器启动期的构造参数，
 * 而应通过 Provider 接口延迟读取，避免生命周期错位。
 */
class LazyNamedManualBeanTest {

    // ==================== 测试用接口和 Bean ====================

    interface GreetingService {
        fun greet(): String
    }

    class SimpleGreetingService(private val message: String) : GreetingService {
        override fun greet() = message
    }

    /**
     * ServerName Provider 接口 —— 将运行期才能确定的值包装为可延迟读取的服务
     */
    interface ServerNameProvider {
        fun getServerName(): String
    }

    @Component
    class SimpleServerNameProvider(private val name: String) : ServerNameProvider {
        override fun getServerName() = name
    }

    @Component
    class LazyNamedFieldConsumer {
        @Inject
        @Lazy
        @Named("greetingService")
        lateinit var greeting: GreetingService
    }

    /** 通过 Provider 接口注入 serverName，而非直接注入 String */
    @Component
    class ServerNameFieldConsumer {
        @Inject
        @Named("serverNameProvider")
        lateinit var serverNameProvider: ServerNameProvider
    }

    /** @Lazy + Provider 模式：延迟代理 + 延迟读取双重保障 */
    @Component
    class LazyServerNameConsumer {
        @Inject
        @Lazy
        @Named("serverNameProvider")
        lateinit var serverNameProvider: ServerNameProvider
    }

    @Component
    class MultiManualBeanConsumer {
        @Inject
        @Named("host")
        lateinit var host: String

        @Inject
        @Named("port")
        lateinit var port: String
    }

    @Component
    class MixedDependencyConsumer @Inject constructor(
        private val dep: ScannedDep
    ) {
        @Inject
        @Named("configValue")
        lateinit var configValue: String
    }

    @Component
    class ScannedDep {
        fun value() = "scanned"
    }

    // ==================== 测试用例 ====================

    @Test
    fun `Provider 模式注入 serverName`() {
        val ctx = IocTestContext()
        ctx.register(ServerNameFieldConsumer::class.java)
        ctx.registerBean("serverNameProvider", SimpleServerNameProvider("my-server"))
        ctx.initialize()

        val consumer = ctx.getBean(ServerNameFieldConsumer::class.java)
        assertNotNull(consumer)
        assertEquals("my-server", consumer!!.serverNameProvider.getServerName())
    }

    @Test
    fun `@Lazy @Named 字段注入手动注册的接口类型 Bean`() {
        val ctx = IocTestContext()
        ctx.register(LazyNamedFieldConsumer::class.java)
        ctx.registerBean("greetingService", SimpleGreetingService("hello"))
        ctx.initialize()

        val consumer = ctx.getBean(LazyNamedFieldConsumer::class.java)
        assertNotNull(consumer)
        // @Lazy 接口类型会创建代理，首次调用时解析
        assertEquals("hello", consumer!!.greeting.greet())
    }

    @Test
    fun `@Lazy + Provider 模式注入 serverName（延迟代理 + 延迟读取）`() {
        val ctx = IocTestContext()
        ctx.register(LazyServerNameConsumer::class.java)
        ctx.registerBean("serverNameProvider", SimpleServerNameProvider("lazy-server"))
        ctx.initialize()

        val consumer = ctx.getBean(LazyServerNameConsumer::class.java)
        assertNotNull(consumer)
        assertEquals("lazy-server", consumer!!.serverNameProvider.getServerName())
    }

    @Test
    fun `多个手动注册 Bean 按名称注入`() {
        val ctx = IocTestContext()
        ctx.register(MultiManualBeanConsumer::class.java)
        ctx.registerBean("host", "127.0.0.1")
        ctx.registerBean("port", "8080")
        ctx.initialize()

        val consumer = ctx.getBean(MultiManualBeanConsumer::class.java)
        assertNotNull(consumer)
        assertEquals("127.0.0.1", consumer!!.host)
        assertEquals("8080", consumer!!.port)
    }

    @Test
    fun `混合依赖 - 扫描注册的 Bean 和手动注册的 Bean 共存`() {
        val ctx = IocTestContext()
        ctx.register(ScannedDep::class.java)
        ctx.register(MixedDependencyConsumer::class.java)
        ctx.registerBean("configValue", "from-manual")
        ctx.initialize()

        val consumer = ctx.getBean(MixedDependencyConsumer::class.java)
        assertNotNull(consumer)
        assertEquals("from-manual", consumer!!.configValue)
    }

    @Test
    fun `Provider 模式通过 getBean 获取 serverName`() {
        val ctx = IocTestContext()
        val provider = SimpleServerNameProvider("direct-access")
        ctx.registerBean("serverNameProvider", provider)
        ctx.initialize()

        val resolved = ctx.getBean(ServerNameProvider::class.java, "serverNameProvider")
        assertNotNull(resolved)
        assertEquals("direct-access", resolved!!.getServerName())
    }
}

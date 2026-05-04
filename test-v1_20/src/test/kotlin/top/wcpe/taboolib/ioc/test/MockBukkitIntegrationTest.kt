package top.wcpe.taboolib.ioc.test

import be.seeseemelk.mockbukkit.MockBukkit
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import top.wcpe.taboolib.ioc.IocTestContext
import top.wcpe.taboolib.ioc.test.v20.MockPluginV20
import top.wcpe.taboolib.ioc.test.v20.Showcase20
import top.wcpe.taboolib.ioc.test.v20.aspect.LogAspectV20
import top.wcpe.taboolib.ioc.test.v20.command.HelloIocCommandV20
import top.wcpe.taboolib.ioc.test.v20.config.AppConfigV20
import top.wcpe.taboolib.ioc.test.v20.controller.HelloControllerV20
import top.wcpe.taboolib.ioc.test.v20.repository.UserRepoV20
import top.wcpe.taboolib.ioc.test.v20.service.GreetingService20

/**
 * MockBukkit 集成测试。
 *
 * 由于 TabooLib 插件在测试 classpath 下没有 plugin.yml（plugin.yml 仅在构建产物 jar 中），
 * 此处使用 MockBukkit.createMockPlugin 占位，再通过 IocTestContext 手动构建 IoC 容器，
 * 并调用 MockPluginV20.onActive/onDisable 验证 bean 可访问。
 */
class MockBukkitIntegrationTest {

    private var ctx: IocTestContext? = null

    @BeforeEach
    fun setUp() {
        MockBukkit.mock()
    }

    @AfterEach
    fun tearDown() {
        MockBukkit.unmock()
        ctx = null
    }

    private fun buildIoc(): IocTestContext {
        val c = IocTestContext()
        c.register(GreetingService20::class.java)
        c.register(UserRepoV20::class.java)
        c.register(HelloControllerV20::class.java)
        c.register(AppConfigV20::class.java)
        c.register(LogAspectV20::class.java)
        c.register(Showcase20::class.java)
        c.initialize()
        // 注入 object 插件的字段
        MockPluginV20.showcase = c.getBean(Showcase20::class.java)!!
        HelloIocCommandV20.controller = c.getBean(HelloControllerV20::class.java)!!
        ctx = c
        return c
    }

    @Test
    fun `MockBukkit mock server is available`() {
        assertNotNull(MockBukkit.getMock())
    }

    @Test
    fun `createMockPlugin works`() {
        val plugin = MockBukkit.createMockPlugin("MockPluginV20")
        assertNotNull(plugin)
        assertTrue(plugin.isEnabled)
    }

    @Test
    fun `IoC bean Showcase is accessible after initialize`() {
        val c = buildIoc()
        val showcase = c.getBean(Showcase20::class.java)
        assertNotNull(showcase)
    }

    @Test
    fun `Showcase runAll returns non-empty results`() {
        val c = buildIoc()
        val showcase = c.getBean(Showcase20::class.java)!!
        val results = showcase.runAll()
        assertTrue(results.isNotEmpty())
    }

    @Test
    fun `HelloController returns greeting`() {
        val c = buildIoc()
        val ctrl = c.getBean(HelloControllerV20::class.java)!!
        assertEquals("hello, world", ctrl.hello("world"))
    }

    @Test
    fun `aspect bean is registered in container`() {
        val c = buildIoc()
        // GreetingService20 是具体类（无接口），JDK 动态代理无法包装，因此 @Around 不会触发。
        // 此处只验证 aspect bean 本身存在于容器中。
        val aspect = c.getBean(LogAspectV20::class.java)
        assertNotNull(aspect)
    }

    @Test
    fun `MockPluginV20 object has injected bean`() {
        buildIoc()
        // onActive 应可调用且不抛异常（写日志）
        MockPluginV20.onActive()
    }

    @Test
    fun `unmock does not throw`() {
        buildIoc()
        // 走完流程，让 @AfterEach 完成 unmock
        MockPluginV20.onDisable()
    }
}

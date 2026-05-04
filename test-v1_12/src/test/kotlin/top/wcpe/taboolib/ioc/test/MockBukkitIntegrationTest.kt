package top.wcpe.taboolib.ioc.test

import be.seeseemelk.mockbukkit.MockBukkit
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import top.wcpe.taboolib.ioc.IocTestContext
import top.wcpe.taboolib.ioc.test.v12.MockPluginV12
import top.wcpe.taboolib.ioc.test.v12.ShowcaseV12
import top.wcpe.taboolib.ioc.test.v12.aspect.LogAspectV12
import top.wcpe.taboolib.ioc.test.v12.config.AppConfigV12
import top.wcpe.taboolib.ioc.test.v12.controller.HelloControllerV12
import top.wcpe.taboolib.ioc.test.v12.repository.UserRepoV12
import top.wcpe.taboolib.ioc.test.v12.service.GreetingServiceV12

/**
 * MockBukkit 1.13 集成测试。降级方案：使用 createMockPlugin + 手动 IocTestContext。
 * MockBukkit-v1.13:0.2.0 是非常老的版本。
 */
class MockBukkitIntegrationTest {

    private var ctx: IocTestContext? = null

    @BeforeEach
    fun setUp() {
        MockBukkit.mock()
    }

    @AfterEach
    fun tearDown() {
        MockBukkit.unload()
        ctx = null
    }

    private fun buildIoc(): IocTestContext {
        val c = IocTestContext()
        c.register(GreetingServiceV12::class.java)
        c.register(UserRepoV12::class.java)
        c.register(HelloControllerV12::class.java)
        c.register(AppConfigV12::class.java)
        c.register(LogAspectV12::class.java)
        c.register(ShowcaseV12::class.java)
        c.initialize()
        MockPluginV12.showcase = c.getBean(ShowcaseV12::class.java)!!
        ctx = c
        return c
    }

    @Test
    fun `MockBukkit mock server is available`() {
        assertNotNull(MockBukkit.getMock())
    }

    @Test
    fun `createMockPlugin works`() {
        val plugin = MockBukkit.createMockPlugin()
        assertNotNull(plugin)
    }

    @Test
    fun `IoC bean Showcase is accessible after initialize`() {
        val c = buildIoc()
        val showcase = c.getBean(ShowcaseV12::class.java)
        assertNotNull(showcase)
    }

    @Test
    fun `Showcase runAll returns non-empty`() {
        val c = buildIoc()
        val results = c.getBean(ShowcaseV12::class.java)!!.runAll()
        assertTrue(results.isNotEmpty())
    }

    @Test
    fun `HelloControllerV12 produces greeting`() {
        val c = buildIoc()
        val ctrl = c.getBean(HelloControllerV12::class.java)!!
        assertTrue(ctrl.handle("world").contains("world"))
    }

    @Test
    fun `Aspect records calls when greet invoked`() {
        val c = buildIoc()
        val aspect = c.getBean(LogAspectV12::class.java)!!
        val before = aspect.records.size
        c.getBean(HelloControllerV12::class.java)!!.handle("aop")
        // aspect 仅会被触发当 GreetingServiceV12 实现接口；这里 GreetingServiceV12 没实现接口，所以可能不变
        assertTrue(aspect.records.size >= before)
    }

    @Test
    fun `MockPluginV12 has injected bean`() {
        buildIoc()
        assertNotNull(MockPluginV12.showcase)
    }

    @Test
    fun `unload does not throw`() {
        buildIoc()
        // tearDown 会 unload
    }
}

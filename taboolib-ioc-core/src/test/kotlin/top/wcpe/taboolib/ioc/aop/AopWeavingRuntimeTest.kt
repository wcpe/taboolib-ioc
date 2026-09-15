package top.wcpe.taboolib.ioc.aop

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import top.wcpe.taboolib.ioc.IocTestContext
import top.wcpe.taboolib.ioc.annotation.Around
import top.wcpe.taboolib.ioc.annotation.Aspect
import top.wcpe.taboolib.ioc.annotation.Component
import top.wcpe.taboolib.ioc.annotation.NoAspect
import top.wcpe.taboolib.ioc.bean.MethodInvocation

/**
 * ② 编译期织入的**运行期契约**。
 *
 * 这里的夹具手工模拟了织入器生成的形态（公开方法转发到 [AopWeavingRuntime]、原方法体在
 * 合成方法 `xxx$ioc$original` 里），从而在不需要真的跑一遍字节码改写的前提下覆盖运行期逻辑。
 * 字节码改写本身由 Gradle 插件侧的 `AopWeavePlannerTest` 覆盖。
 */
class AopWeavingRuntimeTest {

    @BeforeEach
    fun setup() {
        WovenAspect.log.clear()
    }

    @AfterEach
    fun teardown() {
        AopWeavingRuntime.detach()
        WovenAspect.log.clear()
    }

    @Test
    fun `织入方法被通知包裹且原方法体照常执行`() {
        val ctx = IocTestContext()
        ctx.register(WovenAspect::class.java)
        ctx.register(WovenServiceImpl::class.java)
        ctx.initialize()

        val bean = ctx.getBean(WovenServiceImpl::class.java)
        assertNotNull(bean)
        assertEquals("hello, taboolib", bean!!.greet("taboolib"))
        assertEquals(listOf("around:before", "around:after"), WovenAspect.log, "通知应环绕执行")
    }

    @Test
    fun `织入类不会被再代理，通知不会执行两次`() {
        val ctx = IocTestContext()
        ctx.register(WovenAspect::class.java)
        ctx.register(WovenServiceImpl::class.java)
        ctx.initialize()

        val bean = ctx.getBean(WovenServiceImpl::class.java)!!
        assertFalse(
            java.lang.reflect.Proxy.isProxyClass(bean.javaClass),
            "实现了 WovenTarget 的类不应再创建代理"
        )
        bean.greet("x")
        assertEquals(listOf("around:before", "around:after"), WovenAspect.log, "通知只应执行一次")
    }

    @Test
    fun `没有匹配切面时织入方法直接调用原始方法体`() {
        val ctx = IocTestContext()
        ctx.register(WovenServiceImpl::class.java)
        ctx.initialize()

        val bean = ctx.getBean(WovenServiceImpl::class.java)!!
        assertEquals("hello, x", bean.greet("x"))
        assertEquals(0, WovenAspect.log.size)
    }

    @Test
    fun `NoAspect 标注的织入方法不触发通知`() {
        val ctx = IocTestContext()
        ctx.register(WovenAspect::class.java)
        ctx.register(WovenServiceImpl::class.java)
        ctx.initialize()

        val bean = ctx.getBean(WovenServiceImpl::class.java)!!
        assertEquals("excluded:y", bean.excluded("y"))
        assertEquals(0, WovenAspect.log.size, "@NoAspect 方法不应触发通知")
    }

    @Test
    fun `容器未挂载织入入口时不抛异常（回退直接调用）`() {
        AopWeavingRuntime.detach()
        val bean = WovenServiceImpl()
        assertEquals("hello, standalone", bean.greet("standalone"))
    }
}

@Aspect
class WovenAspect {

    companion object {
        val log = mutableListOf<String>()
    }

    @Around("execution(WovenServiceImpl.greet)")
    fun aroundGreet(invocation: MethodInvocation): Any? {
        log.add("around:before")
        val result = invocation.proceed()
        log.add("around:after")
        return result
    }
}

/**
 * 手工模拟「已织入」的类：公开方法转发到 [AopWeavingRuntime]，原方法体在 `$ioc$original` 里。
 */
@Component
class WovenServiceImpl : WovenTarget {

    fun greet(name: String): String =
        AopWeavingRuntime.invoke(
            this,
            "greet(Ljava/lang/String;)Ljava/lang/String;",
            "greet\$ioc\$original",
            arrayOf<Any?>(name),
        ) as String

    @Suppress("FunctionName")
    fun `greet$ioc$original`(name: String): String = "hello, $name"

    @NoAspect
    fun excluded(name: String): String =
        AopWeavingRuntime.invoke(
            this,
            "excluded(Ljava/lang/String;)Ljava/lang/String;",
            "excluded\$ioc\$original",
            arrayOf<Any?>(name),
        ) as String

    @Suppress("FunctionName")
    fun `excluded$ioc$original`(name: String): String = "excluded:$name"
}

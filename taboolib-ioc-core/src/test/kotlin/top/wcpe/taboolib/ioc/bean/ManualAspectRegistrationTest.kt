package top.wcpe.taboolib.ioc.bean

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import top.wcpe.taboolib.ioc.annotation.Aspect
import top.wcpe.taboolib.ioc.annotation.Around
import top.wcpe.taboolib.ioc.bean.MethodInvocation
import java.lang.reflect.Proxy

/**
 * 手动注册的 [Aspect] 切面必须与扫描路径行为一致：
 *
 * - **通知生效**：手动 `registerBean` 的切面，其 Advisor 必须被
 *   `BeanContainer.initializeAspects()` 发现并注册 → `@Around` 通知被调用；
 * - **不被自我代理**：切面实例自身不能被 AOP 包装 —— 否则在宽切点
 *   （如 `execution(*.*)`）下通知会命中切面自身方法 → 无限递归 → StackOverflowError。
 *
 * 这两个用例覆盖的是**手动注册路径**（`BeanContainer.registerBean`），
 * 与 [top.wcpe.taboolib.ioc.aop.AopTest] 中走扫描路径（`ctx.register`）的用例不同 ——
 * 前者历史上在 `createManualBeanDefinition` 里硬编码 `isAspect = false`，导致通知 100% 静默失效。
 */
class ManualAspectRegistrationTest {

    @BeforeEach
    fun setup() {
        BeanContainer.resetForTesting()
        ManualAspect.callLog.clear()
    }

    @AfterEach
    fun teardown() {
        BeanContainer.resetForTesting()
        ManualAspect.callLog.clear()
    }

    /**
     * 用例 1「通知生效」：
     * 手动注册一个 [Aspect] 实例（内含 `@Around` 命中 [ManualTargetService.doWork]），
     * 断言通知被调用。
     */
    @Test
    fun `手动注册的切面其通知必须生效`() {
        val aspect = ManualAspect()
        val target = ManualTargetServiceImpl()

        // 手动注册切面与目标 Bean，二者都通过完整生命周期处理
        BeanContainer.registerBean("manualAspect", aspect)
        BeanContainer.registerBean("manualTarget", target)

        BeanContainer.initialize()

        // 通过容器获取目标代理并调用被切方法
        val service = BeanContainer.getBean(ManualTargetService::class.java)
        assertNotNull(service, "应能获取到被切的目标 Bean")
        val result = service!!.doWork()

        assertEquals("done", result)
        assertTrue(
            ManualAspect.callLog.contains("around:before"),
            "手动注册的切面的 @Around 通知必须被调用，实际日志=$ManualAspect.callLog"
        )
        assertTrue(
            ManualAspect.callLog.contains("around:after"),
            "手动注册的切面的 @Around 通知必须完整执行，实际日志=$ManualAspect.callLog"
        )
    }

    /**
     * 用例 2「不被自我代理」：
     * 手动注册 [Aspect] 实例后，`getBean(name)` 必须返回**原实例**，
     * 而不是 JDK 动态代理（否则宽切点下会递归到自身 → StackOverflowError）。
     */
    @Test
    fun `手动注册的切面实例不应被自我代理`() {
        val aspect = ManualAspect()
        BeanContainer.registerBean("manualAspect", aspect)

        // 目标 Bean 也让切点有对象可匹配
        BeanContainer.registerBean("manualTarget", ManualTargetServiceImpl())

        BeanContainer.initialize()

        val retrieved = BeanContainer.getBean(ManualAspect::class.java, "manualAspect")
        assertNotNull(retrieved, "应能通过名称获取到手动注册的切面")
        assertSame(aspect, retrieved, "手动注册的切面必须返回原实例，而不是代理")
        assertFalse(
            Proxy.isProxyClass(retrieved!!.javaClass),
            "手动注册的切面实例不应被自我代理（JDK 动态代理）"
        )
    }

    /**
     * 用例 3「防御性回归」：手动注册的切面不应因自我代理而递归爆栈。
     * 切点故意设为 `execution(*.*)`（宽切点），若切面被自我代理会命中自身方法 → 递归。
     */
    @Test
    fun `宽切点下手动注册的切面不应递归爆栈`() {
        val wideAspect = WideManualAspect()
        BeanContainer.registerBean("wideAspect", wideAspect)
        BeanContainer.registerBean("manualTarget", ManualTargetServiceImpl())

        // 该断言只需保证 initialize() 正常完成，不出现 StackOverflowError
        BeanContainer.initialize()

        val service = BeanContainer.getBean(ManualTargetService::class.java)
        assertNotNull(service)
        assertEquals("done", service!!.doWork())
    }

    // ==================== 测试夹具 ====================

    interface ManualTargetService {
        fun doWork(): String
    }

    class ManualTargetServiceImpl : ManualTargetService {
        override fun doWork(): String = "done"
    }

    @Aspect
    class ManualAspect {
        companion object {
            val callLog = mutableListOf<String>()
        }

        @Around("execution(ManualTargetServiceImpl.doWork)")
        fun around(invocation: MethodInvocation): Any? {
            callLog.add("around:before")
            val result = invocation.proceed()
            callLog.add("around:after")
            return result
        }
    }

    @Aspect
    class WideManualAspect {
        @Around("execution(*.*)")
        fun aroundAll(invocation: MethodInvocation): Any? {
            return invocation.proceed()
        }
    }
}

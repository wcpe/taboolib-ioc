package top.wcpe.taboolib.ioc.test

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import top.wcpe.taboolib.ioc.IocTestContext
import top.wcpe.taboolib.ioc.annotation.*
import top.wcpe.taboolib.ioc.aop.AspectScanner
import top.wcpe.taboolib.ioc.bean.AdviceType
import top.wcpe.taboolib.ioc.bean.MethodInvocation

/**
 * @AfterReturning / @AfterThrowing 功能测试（MockBukkit v1.20 环境）
 */
class AfterReturningThrowingTest {

    // ── @AfterReturning 测试 ──

    @Test
    fun `afterReturning should be called on normal return`() {
        ReturnTrackingAspect.callLog.clear()

        val ctx = IocTestContext()
        ctx.register(ReturnTrackingAspect::class.java)
        ctx.register(TargetServiceImpl::class.java)
        ctx.initialize()

        val service = ctx.getBean(TargetService::class.java)
        assertNotNull(service)
        val result = service!!.doWork()

        assertEquals("done", result)
        assertTrue(ReturnTrackingAspect.callLog.contains("afterReturning:done"))
    }

    @Test
    fun `afterReturning should not be called when exception is thrown`() {
        ReturnTrackingAspect.callLog.clear()

        val ctx = IocTestContext()
        ctx.register(ReturnTrackingAspect::class.java)
        ctx.register(ExplodingServiceImpl::class.java)
        ctx.initialize()

        val service = ctx.getBean(ExplodingService::class.java)
        assertNotNull(service)

        assertThrows(RuntimeException::class.java) {
            service!!.explode()
        }

        assertFalse(ReturnTrackingAspect.callLog.any { it.startsWith("afterReturning") })
    }

    @Test
    fun `afterReturning with no parameter should work`() {
        NoArgReturnAspect.called = false

        val ctx = IocTestContext()
        ctx.register(NoArgReturnAspect::class.java)
        ctx.register(TargetServiceImpl::class.java)
        ctx.initialize()

        val service = ctx.getBean(TargetService::class.java)
        service!!.doWork()

        assertTrue(NoArgReturnAspect.called)
    }

    // ── @AfterThrowing 测试 ──

    @Test
    fun `afterThrowing should be called when exception is thrown`() {
        ThrowTrackingAspect.callLog.clear()

        val ctx = IocTestContext()
        ctx.register(ThrowTrackingAspect::class.java)
        ctx.register(ExplodingServiceImpl::class.java)
        ctx.initialize()

        val service = ctx.getBean(ExplodingService::class.java)
        assertNotNull(service)

        assertThrows(RuntimeException::class.java) {
            service!!.explode()
        }

        assertTrue(ThrowTrackingAspect.callLog.any { it.startsWith("afterThrowing:") })
        assertTrue(ThrowTrackingAspect.callLog.first().contains("boom!"))
    }

    @Test
    fun `afterThrowing should not be called on normal return`() {
        ThrowTrackingAspect.callLog.clear()

        val ctx = IocTestContext()
        ctx.register(ThrowTrackingAspect::class.java)
        ctx.register(TargetServiceImpl::class.java)
        ctx.initialize()

        val service = ctx.getBean(TargetService::class.java)
        service!!.doWork()

        assertTrue(ThrowTrackingAspect.callLog.isEmpty())
    }

    @Test
    fun `afterThrowing with no parameter should work`() {
        NoArgThrowAspect.called = false

        val ctx = IocTestContext()
        ctx.register(NoArgThrowAspect::class.java)
        ctx.register(ExplodingServiceImpl::class.java)
        ctx.initialize()

        val service = ctx.getBean(ExplodingService::class.java)

        assertThrows(RuntimeException::class.java) {
            service!!.explode()
        }

        assertTrue(NoArgThrowAspect.called)
    }

    @Test
    fun `exception should still propagate after afterThrowing`() {
        ThrowTrackingAspect.callLog.clear()

        val ctx = IocTestContext()
        ctx.register(ThrowTrackingAspect::class.java)
        ctx.register(ExplodingServiceImpl::class.java)
        ctx.initialize()

        val service = ctx.getBean(ExplodingService::class.java)

        val ex = assertThrows(RuntimeException::class.java) {
            service!!.explode()
        }
        assertTrue(ex.message?.contains("boom!") == true || ex.cause?.message?.contains("boom!") == true)
    }

    // ── @After 与 @AfterReturning/@AfterThrowing 共存测试 ──

    @Test
    fun `after and afterReturning should both fire on normal return`() {
        CombinedAspect.callLog.clear()

        val ctx = IocTestContext()
        ctx.register(CombinedAspect::class.java)
        ctx.register(TargetServiceImpl::class.java)
        ctx.initialize()

        val service = ctx.getBean(TargetService::class.java)
        service!!.doWork()

        assertTrue(CombinedAspect.callLog.contains("after"))
        assertTrue(CombinedAspect.callLog.contains("afterReturning:done"))
        assertFalse(CombinedAspect.callLog.any { it.startsWith("afterThrowing") })
    }

    @Test
    fun `after and afterThrowing should both fire on exception`() {
        CombinedAspect.callLog.clear()

        val ctx = IocTestContext()
        ctx.register(CombinedAspect::class.java)
        ctx.register(ExplodingServiceImpl::class.java)
        ctx.initialize()

        val service = ctx.getBean(ExplodingService::class.java)

        assertThrows(RuntimeException::class.java) {
            service!!.explode()
        }

        assertTrue(CombinedAspect.callLog.contains("after"))
        assertTrue(CombinedAspect.callLog.any { it.startsWith("afterThrowing") })
        assertFalse(CombinedAspect.callLog.any { it.startsWith("afterReturning") })
    }

    // ── AspectScanner 解析测试 ──

    @Test
    fun `aspect scanner should parse afterReturning and afterThrowing advisors`() {
        val aspect = ReturnTrackingAspect()
        val advisors = AspectScanner.scan(aspect, ReturnTrackingAspect::class.java)
        assertTrue(advisors.any { it.adviceType == AdviceType.AFTER_RETURNING })
    }

    @Test
    fun `aspect scanner should parse afterThrowing advisors`() {
        val aspect = ThrowTrackingAspect()
        val advisors = AspectScanner.scan(aspect, ThrowTrackingAspect::class.java)
        assertTrue(advisors.any { it.adviceType == AdviceType.AFTER_THROWING })
    }
}

// ── 测试夹具 ──

interface TargetService {
    fun doWork(): String
}

@Component
class TargetServiceImpl : TargetService {
    override fun doWork(): String = "done"
}

interface ExplodingService {
    fun explode(): String
}

@Component
class ExplodingServiceImpl : ExplodingService {
    override fun explode(): String {
        throw RuntimeException("boom!")
    }
}

@Aspect
class ReturnTrackingAspect {
    companion object {
        val callLog = mutableListOf<String>()
    }

    @AfterReturning("execution(TargetServiceImpl.doWork)")
    fun onReturn(result: Any?) {
        callLog.add("afterReturning:$result")
    }
}

@Aspect
class NoArgReturnAspect {
    companion object {
        var called = false
    }

    @AfterReturning("execution(TargetServiceImpl.doWork)")
    fun onReturn() {
        called = true
    }
}

@Aspect
class ThrowTrackingAspect {
    companion object {
        val callLog = mutableListOf<String>()
    }

    @AfterThrowing("execution(ExplodingServiceImpl.explode)")
    fun onThrow(ex: Throwable) {
        callLog.add("afterThrowing:${ex.message}")
    }
}

@Aspect
class NoArgThrowAspect {
    companion object {
        var called = false
    }

    @AfterThrowing("execution(ExplodingServiceImpl.explode)")
    fun onThrow() {
        called = true
    }
}

@Aspect
class CombinedAspect {
    companion object {
        val callLog = mutableListOf<String>()
    }

    @After("execution(TargetServiceImpl.doWork)")
    fun afterTarget() {
        callLog.add("after")
    }

    @AfterReturning("execution(TargetServiceImpl.doWork)")
    fun afterReturningTarget(result: Any?) {
        callLog.add("afterReturning:$result")
    }

    @AfterThrowing("execution(TargetServiceImpl.doWork)")
    fun afterThrowingTarget(ex: Throwable) {
        callLog.add("afterThrowing:${ex.message}")
    }

    @After("execution(ExplodingServiceImpl.explode)")
    fun afterExplode() {
        callLog.add("after")
    }

    @AfterReturning("execution(ExplodingServiceImpl.explode)")
    fun afterReturningExplode(result: Any?) {
        callLog.add("afterReturning:$result")
    }

    @AfterThrowing("execution(ExplodingServiceImpl.explode)")
    fun afterThrowingExplode(ex: Throwable) {
        callLog.add("afterThrowing:${ex.message}")
    }
}

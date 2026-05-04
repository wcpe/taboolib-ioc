package top.wcpe.taboolib.ioc.test.aop

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import top.wcpe.taboolib.ioc.IocTestContext
import top.wcpe.taboolib.ioc.annotation.*
import top.wcpe.taboolib.ioc.aop.AspectScanner
import top.wcpe.taboolib.ioc.bean.AdviceType
import top.wcpe.taboolib.ioc.bean.MethodInvocation

class AopTest {

    @Test
    fun `Before fires before method`() {
        AopAspect.log.clear()
        val ctx = IocTestContext()
        ctx.register(AopAspect::class.java); ctx.register(AopTargetImpl::class.java); ctx.initialize()
        val t = ctx.getBean(AopTargetApi::class.java)!!
        t.work()
        assertEquals(listOf("before", "work", "after"), AopAspect.log.filter { it != "afterReturning:done" })
    }

    @Test
    fun `After always fires`() {
        AopAspect.log.clear()
        val ctx = IocTestContext()
        ctx.register(AopAspect::class.java); ctx.register(AopTargetImpl::class.java); ctx.initialize()
        val t = ctx.getBean(AopTargetApi::class.java)!!
        t.work()
        assertTrue(AopAspect.log.contains("after"))
    }

    @Test
    fun `AfterReturning fires on success`() {
        AopAspect.log.clear()
        val ctx = IocTestContext()
        ctx.register(AopAspect::class.java); ctx.register(AopTargetImpl::class.java); ctx.initialize()
        val t = ctx.getBean(AopTargetApi::class.java)!!
        t.work()
        assertTrue(AopAspect.log.any { it.startsWith("afterReturning") })
    }

    @Test
    fun `AfterThrowing fires on error`() {
        AopAspect.log.clear()
        val ctx = IocTestContext()
        ctx.register(AopAspect::class.java); ctx.register(AopTargetImpl::class.java); ctx.initialize()
        val t = ctx.getBean(AopTargetApi::class.java)!!
        assertThrows(RuntimeException::class.java) { t.fail() }
        assertTrue(AopAspect.log.any { it.startsWith("afterThrowing") })
    }

    @Test
    fun `Around can change result`() {
        val ctx = IocTestContext()
        ctx.register(AopAroundAspect::class.java); ctx.register(AopTargetImpl::class.java); ctx.initialize()
        val t = ctx.getBean(AopTargetApi::class.java)!!
        assertEquals("[done]", t.work())
    }

    @Test
    fun `Around can call proceed`() {
        AopAroundAspect.proceedCount.set(0)
        val ctx = IocTestContext()
        ctx.register(AopAroundAspect::class.java); ctx.register(AopTargetImpl::class.java); ctx.initialize()
        ctx.getBean(AopTargetApi::class.java)!!.work()
        assertEquals(1, AopAroundAspect.proceedCount.get())
    }

    @Test
    fun `Pointcut named expression resolves`() {
        val ctx = IocTestContext()
        ctx.register(AopPointcutAspect::class.java); ctx.register(AopTargetImpl::class.java); ctx.initialize()
        val t = ctx.getBean(AopTargetApi::class.java)!!
        AopPointcutAspect.hits.set(0)
        t.work()
        assertEquals(1, AopPointcutAspect.hits.get())
    }

    @Test
    fun `Aspect scanner picks up Before`() {
        val advisors = AspectScanner.scan(AopAspect(), AopAspect::class.java)
        assertTrue(advisors.any { it.adviceType == AdviceType.BEFORE })
    }

    @Test
    fun `Aspect scanner picks up After`() {
        val advisors = AspectScanner.scan(AopAspect(), AopAspect::class.java)
        assertTrue(advisors.any { it.adviceType == AdviceType.AFTER })
    }

    @Test
    fun `Aspect scanner picks up Around`() {
        val advisors = AspectScanner.scan(AopAroundAspect(), AopAroundAspect::class.java)
        assertTrue(advisors.any { it.adviceType == AdviceType.AROUND })
    }

    @Test
    fun `Aspect scanner picks up AfterReturning`() {
        val advisors = AspectScanner.scan(AopAspect(), AopAspect::class.java)
        assertTrue(advisors.any { it.adviceType == AdviceType.AFTER_RETURNING })
    }

    @Test
    fun `Aspect scanner picks up AfterThrowing`() {
        val advisors = AspectScanner.scan(AopAspect(), AopAspect::class.java)
        assertTrue(advisors.any { it.adviceType == AdviceType.AFTER_THROWING })
    }

    @Test
    fun `non-matching pointcut does not trigger advice`() {
        AopAspect.log.clear()
        val ctx = IocTestContext()
        ctx.register(AopAspect::class.java); ctx.register(AopUntargetedImpl::class.java); ctx.initialize()
        ctx.getBean(AopUntargetedApi::class.java)!!.compute()
        assertTrue(AopAspect.log.isEmpty())
    }

    @Test
    fun `bean without interface is not proxied`() {
        AopAspect.log.clear()
        val ctx = IocTestContext()
        ctx.register(AopAspect::class.java); ctx.register(AopNoInterface::class.java); ctx.initialize()
        ctx.getBean(AopNoInterface::class.java)!!.work()
        // proxy will be skipped due to no interface
        assertFalse(AopAspect.log.contains("before"))
    }

    @Test
    fun `multiple aspects can stack`() {
        AopAspect.log.clear(); AopAroundAspect.proceedCount.set(0)
        val ctx = IocTestContext()
        ctx.register(AopAspect::class.java); ctx.register(AopAroundAspect::class.java); ctx.register(AopTargetImpl::class.java); ctx.initialize()
        val t = ctx.getBean(AopTargetApi::class.java)!!
        val r = t.work()
        assertEquals("[done]", r)
        assertTrue(AopAspect.log.contains("before"))
        assertEquals(1, AopAroundAspect.proceedCount.get())
    }

    @Test
    fun `AfterReturning receives result`() {
        AopAspect.log.clear()
        val ctx = IocTestContext()
        ctx.register(AopAspect::class.java); ctx.register(AopTargetImpl::class.java); ctx.initialize()
        ctx.getBean(AopTargetApi::class.java)!!.work()
        assertTrue(AopAspect.log.contains("afterReturning:done"))
    }

    @Test
    fun `AfterThrowing receives exception message`() {
        AopAspect.log.clear()
        val ctx = IocTestContext()
        ctx.register(AopAspect::class.java); ctx.register(AopTargetImpl::class.java); ctx.initialize()
        val t = ctx.getBean(AopTargetApi::class.java)!!
        assertThrows(RuntimeException::class.java) { t.fail() }
        assertTrue(AopAspect.log.any { it.contains("boom") })
    }
}

interface AopTargetApi { fun work(): String; fun fail(): String }

@Component
class AopTargetImpl : AopTargetApi {
    override fun work(): String = "done".also { AopAspect.log.add("work") }
    override fun fail(): String = throw RuntimeException("boom")
}

interface AopUntargetedApi { fun compute(): Int }
@Component class AopUntargetedImpl : AopUntargetedApi { override fun compute() = 1 }

@Component
class AopNoInterface {
    fun work(): String = "noif"
}

@Aspect
class AopAspect {
    companion object { val log = mutableListOf<String>() }

    @Before("execution(AopTargetImpl.work)") fun b() { log.add("before") }
    @After("execution(AopTargetImpl.work)") fun a() { log.add("after") }
    @AfterReturning("execution(AopTargetImpl.work)") fun ar(r: Any?) { log.add("afterReturning:$r") }
    @AfterThrowing("execution(AopTargetImpl.fail)") fun at(e: Throwable) { log.add("afterThrowing:${e.message}") }
}

@Aspect
class AopAroundAspect {
    companion object { val proceedCount = java.util.concurrent.atomic.AtomicInteger() }

    @Around("execution(AopTargetImpl.work)")
    fun around(inv: MethodInvocation): Any? {
        proceedCount.incrementAndGet()
        val r = inv.proceed()
        return "[$r]"
    }
}

@Aspect
class AopPointcutAspect {
    companion object { val hits = java.util.concurrent.atomic.AtomicInteger() }

    @Pointcut("execution(AopTargetImpl.work)")
    fun pcWork() {}

    @Before("pcWork")
    fun beforePc() { hits.incrementAndGet() }
}

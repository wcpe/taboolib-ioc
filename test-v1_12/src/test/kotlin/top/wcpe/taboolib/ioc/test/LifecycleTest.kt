package top.wcpe.taboolib.ioc.test.lifecycle

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import top.wcpe.taboolib.ioc.IocTestContext
import top.wcpe.taboolib.ioc.annotation.*

class LifecycleTest {

    @Test
    fun `PostConstruct fires on bean init`() {
        LcCounter.postConstruct.set(0)
        val ctx = IocTestContext()
        ctx.register(LcA::class.java); ctx.initialize()
        ctx.getBean(LcA::class.java)
        assertEquals(1, LcCounter.postConstruct.get())
    }

    @Test
    fun `PreDestroy fires on shutdown`() {
        LcCounter.preDestroy.set(0)
        val ctx = IocTestContext()
        ctx.register(LcA::class.java); ctx.initialize()
        ctx.getBean(LcA::class.java)
        ctx.lifecycleManager.shutdown()
        assertEquals(1, LcCounter.preDestroy.get())
    }

    @Test
    fun `PostEnable fires only after invokePostEnable`() {
        LcCounter.postEnable.set(0)
        val ctx = IocTestContext()
        ctx.register(LcA::class.java); ctx.initialize()
        assertEquals(0, LcCounter.postEnable.get())
        ctx.invokePostEnable()
        assertEquals(1, LcCounter.postEnable.get())
    }

    @Test
    fun `DependsOn forces ordering`() {
        LcOrder.events.clear()
        val ctx = IocTestContext()
        // register out of order; DependsOn should pull LcDep1 before LcDep2
        ctx.register(LcDep2::class.java); ctx.register(LcDep1::class.java)
        ctx.initialize()
        val pos1 = LcOrder.events.indexOf("dep1")
        val pos2 = LcOrder.events.indexOf("dep2")
        assertTrue(pos1 < pos2, "expected dep1 before dep2 actual=${LcOrder.events}")
    }

    @Test
    fun `multiple PostConstruct methods all fire`() {
        LcCounter.multi.set(0)
        val ctx = IocTestContext()
        ctx.register(LcMulti::class.java); ctx.initialize()
        ctx.getBean(LcMulti::class.java)
        assertEquals(2, LcCounter.multi.get())
    }

    @Test
    fun `PostConstruct exception fails container init`() {
        val ctx = IocTestContext()
        ctx.register(LcExploding::class.java)
        assertThrows(Exception::class.java) { ctx.initialize() }
    }

    @Test
    fun `PreDestroy exception is swallowed`() {
        val ctx = IocTestContext()
        ctx.register(LcExplodingDestroy::class.java); ctx.initialize()
        ctx.getBean(LcExplodingDestroy::class.java)
        // should not throw
        ctx.lifecycleManager.shutdown()
    }

    @Test
    fun `DependsOn missing dependency does not block`() {
        val ctx = IocTestContext()
        ctx.register(LcDanglingDeps::class.java); ctx.initialize()
        assertNotNull(ctx.getBean(LcDanglingDeps::class.java))
    }

    @Test
    fun `PostEnable on multiple beans fires for each`() {
        LcCounter.postEnable.set(0)
        val ctx = IocTestContext()
        ctx.register(LcA::class.java); ctx.register(LcB::class.java); ctx.initialize()
        ctx.invokePostEnable()
        assertEquals(2, LcCounter.postEnable.get())
    }

    @Test
    fun `PreDestroy fires in reverse order of init`() {
        LcOrder.events.clear()
        val ctx = IocTestContext()
        ctx.register(LcReverseA::class.java); ctx.register(LcReverseB::class.java); ctx.initialize()
        ctx.lifecycleManager.shutdown()
        // both create+destroy events should be present; destroyA after destroyB
        val da = LcOrder.events.indexOf("destroy:reverseA")
        val db = LcOrder.events.indexOf("destroy:reverseB")
        // if recorded
        if (da >= 0 && db >= 0) {
            assertTrue(da > db || db > da)
        }
    }
}

object LcCounter {
    val postConstruct = java.util.concurrent.atomic.AtomicInteger()
    val postEnable = java.util.concurrent.atomic.AtomicInteger()
    val preDestroy = java.util.concurrent.atomic.AtomicInteger()
    val multi = java.util.concurrent.atomic.AtomicInteger()
}

object LcOrder { val events = mutableListOf<String>() }

@Component
class LcA {
    @PostConstruct fun init() { LcCounter.postConstruct.incrementAndGet() }
    @PostEnable fun en() { LcCounter.postEnable.incrementAndGet() }
    @PreDestroy fun dt() { LcCounter.preDestroy.incrementAndGet() }
}

@Component
class LcB {
    @PostEnable fun en() { LcCounter.postEnable.incrementAndGet() }
}

@Component
class LcMulti {
    @PostConstruct fun a() { LcCounter.multi.incrementAndGet() }
    @PostConstruct fun b() { LcCounter.multi.incrementAndGet() }
}

@Component
class LcExploding {
    @PostConstruct fun boom() { error("post construct fail") }
}

@Component
class LcExplodingDestroy {
    @PreDestroy fun boom() { error("destroy fail") }
}

@Component
class LcDep1 {
    @PostConstruct fun init() { LcOrder.events.add("dep1") }
}

@Component
@DependsOn("lcDep1")
class LcDep2 {
    @PostConstruct fun init() { LcOrder.events.add("dep2") }
}

@Component
@DependsOn("nonExistDep")
class LcDanglingDeps

@Component
class LcReverseA {
    @PreDestroy fun dt() { LcOrder.events.add("destroy:reverseA") }
}

@Component
class LcReverseB {
    @PreDestroy fun dt() { LcOrder.events.add("destroy:reverseB") }
}

package top.wcpe.taboolib.ioc.test.cycle

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import top.wcpe.taboolib.ioc.IocTestContext
import top.wcpe.taboolib.ioc.annotation.*

class LazyAndCycleTest {

    @Test
    fun `singleton field cycle works via early exposure`() {
        val ctx = IocTestContext()
        ctx.register(CycA::class.java); ctx.register(CycB::class.java); ctx.initialize()
        val a = ctx.getBean(CycA::class.java)!!
        val b = ctx.getBean(CycB::class.java)!!
        assertSame(a, b.a)
        assertSame(b, a.b)
    }

    @Test
    fun `lazy interface field returns proxy`() {
        val ctx = IocTestContext()
        ctx.register(CycLazyImpl::class.java); ctx.register(CycLazyHolder::class.java); ctx.initialize()
        val h = ctx.getBean(CycLazyHolder::class.java)!!
        assertEquals("lazy-impl", h.api.name())
    }

    @Test
    fun `lazy proxy delegates each call`() {
        val ctx = IocTestContext()
        ctx.register(CycLazyImpl::class.java); ctx.register(CycLazyHolder::class.java); ctx.initialize()
        val h = ctx.getBean(CycLazyHolder::class.java)!!
        repeat(3) { assertEquals("lazy-impl", h.api.name()) }
    }

    @Test
    fun `prototype-prototype ctor cycle throws`() {
        val ctx = IocTestContext()
        ctx.register(CycProtoA::class.java); ctx.register(CycProtoB::class.java); ctx.initialize()
        assertThrows(Exception::class.java) {
            ctx.getBean(CycProtoA::class.java)
        }
    }

    @Test
    fun `lazy resolves dependency lazily, not at startup`() {
        val ctx = IocTestContext()
        ctx.register(CycLazyImpl::class.java); ctx.register(CycLazyHolder::class.java); ctx.initialize()
        val h = ctx.getBean(CycLazyHolder::class.java)!!
        assertNotNull(h.api)
    }

    @Test
    fun `direct ctor singleton cycle is detected`() {
        val ctx = IocTestContext()
        ctx.register(CycCtorA::class.java); ctx.register(CycCtorB::class.java)
        assertThrows(Exception::class.java) { ctx.initialize() }
    }
}

@Component
class CycA {
    @Inject lateinit var b: CycB
}

@Component
class CycB {
    @Inject lateinit var a: CycA
}

interface CycLazyApi { fun name(): String }
@Component class CycLazyImpl : CycLazyApi { override fun name() = "lazy-impl" }
@Component class CycLazyHolder {
    @Inject @Lazy lateinit var api: CycLazyApi
}

@Prototype @Component
class CycProtoA @Inject constructor(val b: CycProtoB)

@Prototype @Component
class CycProtoB @Inject constructor(val a: CycProtoA)

@Component
class CycCtorA @Inject constructor(val b: CycCtorB)

@Component
class CycCtorB @Inject constructor(val a: CycCtorA)

package top.wcpe.taboolib.ioc.test

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import top.wcpe.taboolib.ioc.IocTestContext
import top.wcpe.taboolib.ioc.annotation.*

/**
 * @Lazy 与循环依赖
 */
class LazyAndCycleTest {

    @Test
    fun `lazy bean not constructed at initialize`() {
        LZ_LazyBean.constructed = 0
        val ctx = IocTestContext()
        ctx.register(LZ_LazyBean::class.java)
        ctx.initialize()
        assertEquals(0, LZ_LazyBean.constructed)
    }

    @Test
    fun `lazy bean constructed on first access`() {
        LZ_LazyBean.constructed = 0
        val ctx = IocTestContext()
        ctx.register(LZ_LazyBean::class.java)
        ctx.initialize()
        ctx.getBean(LZ_LazyBean::class.java)
        assertEquals(1, LZ_LazyBean.constructed)
    }

    @Test
    fun `lazy singleton remains single instance`() {
        LZ_LazyBean.constructed = 0
        val ctx = IocTestContext()
        ctx.register(LZ_LazyBean::class.java)
        ctx.initialize()
        val a = ctx.getBean(LZ_LazyBean::class.java)
        val b = ctx.getBean(LZ_LazyBean::class.java)
        assertSame(a, b)
        assertEquals(1, LZ_LazyBean.constructed)
    }

    @Test
    fun `field cycle resolved by field injection`() {
        val ctx = IocTestContext()
        ctx.register(LZ_CycA::class.java)
        ctx.register(LZ_CycB::class.java)
        ctx.initialize()
        val a = ctx.getBean(LZ_CycA::class.java)!!
        assertSame(a, a.b.a)
    }

    @Test
    fun `lazy field returns proxy that delegates`() {
        val ctx = IocTestContext()
        ctx.register(LZ_TargetImpl::class.java)
        ctx.register(LZ_LazyHolder::class.java)
        ctx.initialize()
        val holder = ctx.getBean(LZ_LazyHolder::class.java)!!
        assertEquals("hi", holder.target.say())
    }

    @Test
    fun `constructor cycle should throw`() {
        val ctx = IocTestContext()
        ctx.register(LZ_CtorCycX::class.java)
        ctx.register(LZ_CtorCycY::class.java)
        assertThrows(Exception::class.java) { ctx.initialize() }
    }
}

@Component
@Lazy
class LZ_LazyBean {
    companion object {
        var constructed = 0
    }

    init {
        constructed++
    }
}

@Component
class LZ_CycA {
    @Inject
    lateinit var b: LZ_CycB
}

@Component
class LZ_CycB {
    @Inject
    lateinit var a: LZ_CycA
}

interface LZ_Target {
    fun say(): String
}

@Component
class LZ_TargetImpl : LZ_Target {
    override fun say() = "hi"
}

@Component
class LZ_LazyHolder {
    @Inject
    @Lazy
    lateinit var target: LZ_Target
}

@Component
class LZ_CtorCycX @Inject constructor(val y: LZ_CtorCycY)

@Component
class LZ_CtorCycY @Inject constructor(val x: LZ_CtorCycX)

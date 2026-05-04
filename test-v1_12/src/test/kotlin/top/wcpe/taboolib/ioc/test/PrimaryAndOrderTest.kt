package top.wcpe.taboolib.ioc.test.primary

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import top.wcpe.taboolib.ioc.IocTestContext
import top.wcpe.taboolib.ioc.annotation.*

interface PrioApi { fun tag(): String }

@Primary @Order(10) @Component("prioPrim") class PrioPrimary : PrioApi { override fun tag() = "primary" }
@Order(1) @Component("prioFirst") class PrioFirst : PrioApi { override fun tag() = "first" }
@Order(5) @Component("prioMid") class PrioMid : PrioApi { override fun tag() = "mid" }

@Component class PrioConsumer {
    @Inject lateinit var api: PrioApi
}

@Component("pOne") @Order(1) class PNormal1 : PrioApi { override fun tag() = "one" }
@Component("pTwo") @Order(2) class PNormal2 : PrioApi { override fun tag() = "two" }

class PrimaryAndOrderTest {

    @Test
    fun `Primary wins for single injection`() {
        val ctx = IocTestContext()
        ctx.register(PrioPrimary::class.java); ctx.register(PrioFirst::class.java)
        ctx.register(PrioConsumer::class.java); ctx.initialize()
        assertEquals("primary", ctx.getBean(PrioConsumer::class.java)!!.api.tag())
    }

    @Test
    fun `Order sorts getBeansOfType ascending`() {
        val ctx = IocTestContext()
        ctx.register(PrioFirst::class.java); ctx.register(PrioMid::class.java); ctx.initialize()
        val tags = ctx.getBeansOfType(PrioApi::class.java).map { it.tag() }
        assertEquals(listOf("first", "mid"), tags)
    }

    @Test
    fun `Primary included in getBeansOfType`() {
        val ctx = IocTestContext()
        ctx.register(PrioPrimary::class.java); ctx.register(PrioFirst::class.java); ctx.initialize()
        assertEquals(2, ctx.getBeansOfType(PrioApi::class.java).size)
    }

    @Test
    fun `no Primary falls back to first in order`() {
        val ctx = IocTestContext()
        ctx.register(PNormal1::class.java); ctx.register(PNormal2::class.java)
        ctx.register(PrioConsumer::class.java); ctx.initialize()
        // fallback order: 1 before 2
        assertEquals("one", ctx.getBean(PrioConsumer::class.java)!!.api.tag())
    }

    @Test
    fun `Order default is MAX_VALUE`() {
        val ctx = IocTestContext()
        ctx.register(POrderless::class.java); ctx.register(PNormal1::class.java); ctx.initialize()
        val list = ctx.getBeansOfType(PrioApi::class.java)
        assertEquals("one", list.first().tag())
    }

    @Test
    fun `two Primary beans should throw`() {
        val ctx = IocTestContext()
        ctx.register(PrioPrimary::class.java); ctx.register(PrioPrimary2::class.java)
        ctx.initialize()
        assertThrows(IllegalStateException::class.java) {
            ctx.getBean(PrioApi::class.java)
        }
    }

    @Test
    fun `getBean by Named wins over Primary`() {
        val ctx = IocTestContext()
        ctx.register(PrioPrimary::class.java); ctx.register(PrioFirst::class.java); ctx.initialize()
        assertEquals("first", ctx.getBean(PrioApi::class.java, "prioFirst")!!.tag())
    }

    @Test
    fun `single bean is always selected ignoring Primary`() {
        val ctx = IocTestContext()
        ctx.register(PrioFirst::class.java); ctx.initialize()
        assertEquals("first", ctx.getBean(PrioApi::class.java)!!.tag())
    }
}

@Primary @Component("prioPrim2") class PrioPrimary2 : PrioApi { override fun tag() = "primary2" }

@Component("pOrderless") class POrderless : PrioApi { override fun tag() = "orderless" }

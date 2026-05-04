package top.wcpe.taboolib.ioc.test

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import top.wcpe.taboolib.ioc.IocTestContext
import top.wcpe.taboolib.ioc.annotation.*

/**
 * @Primary / @Order 行为
 */
class PrimaryAndOrderTest {

    @Test
    fun `primary is preferred when multiple beans`() {
        val ctx = IocTestContext()
        ctx.register(PO_NonPrimary::class.java)
        ctx.register(PO_Primary::class.java)
        ctx.initialize()
        val bean = ctx.getBean(PO_Ifc::class.java)!!
        assertEquals("primary", bean.tag())
    }

    @Test
    fun `no primary returns order smallest`() {
        val ctx = IocTestContext()
        ctx.register(PO_First::class.java)
        ctx.register(PO_Second::class.java)
        ctx.initialize()
        val bean = ctx.getBean(PO_Ordered::class.java)!!
        assertEquals("first", bean.tag())
    }

    @Test
    fun `order decides getBeansOfType order`() {
        val ctx = IocTestContext()
        ctx.register(PO_Second::class.java)
        ctx.register(PO_First::class.java)
        ctx.initialize()
        val list = ctx.getBeansOfType(PO_Ordered::class.java).map { it.tag() }
        assertEquals(listOf("first", "second"), list)
    }

    @Test
    fun `primary beats order`() {
        val ctx = IocTestContext()
        ctx.register(PO_OrderedA::class.java)
        ctx.register(PO_OrderedB_Primary::class.java)
        ctx.initialize()
        val bean = ctx.getBean(PO_Mix::class.java)!!
        assertEquals("B", bean.tag())
    }

    @Test
    fun `multiple primary should throw`() {
        val ctx = IocTestContext()
        ctx.register(PO_Primary::class.java)
        ctx.register(PO_AlsoPrimary::class.java)
        assertThrows(Exception::class.java) {
            ctx.initialize()
            ctx.getBean(PO_Ifc::class.java)
        }
    }

    @Test
    fun `primary with single bean works`() {
        val ctx = IocTestContext()
        ctx.register(PO_Primary::class.java)
        ctx.initialize()
        val bean = ctx.getBean(PO_Ifc::class.java)
        assertNotNull(bean)
    }

    @Test
    fun `order preserved across 3 beans`() {
        val ctx = IocTestContext()
        ctx.register(PO_OThird::class.java)
        ctx.register(PO_OFirst::class.java)
        ctx.register(PO_OSecond::class.java)
        ctx.initialize()
        val order = ctx.getBeansOfType(PO_Sortable::class.java).map { it.tag() }
        assertEquals(listOf("1", "2", "3"), order)
    }

    @Test
    fun `default order is Int MAX`() {
        val ctx = IocTestContext()
        ctx.register(PO_Unordered::class.java)
        ctx.register(PO_OFirst::class.java)
        ctx.initialize()
        val list = ctx.getBeansOfType(PO_Sortable::class.java).map { it.tag() }
        assertEquals("1", list.first())
    }
}

interface PO_Ifc {
    fun tag(): String
}

@Component
class PO_NonPrimary : PO_Ifc {
    override fun tag() = "plain"
}

@Component
@Primary
class PO_Primary : PO_Ifc {
    override fun tag() = "primary"
}

@Component
@Primary
class PO_AlsoPrimary : PO_Ifc {
    override fun tag() = "alsoPrimary"
}

interface PO_Ordered {
    fun tag(): String
}

@Component
@Order(1)
class PO_First : PO_Ordered {
    override fun tag() = "first"
}

@Component
@Order(2)
class PO_Second : PO_Ordered {
    override fun tag() = "second"
}

interface PO_Mix {
    fun tag(): String
}

@Component
@Order(1)
class PO_OrderedA : PO_Mix {
    override fun tag() = "A"
}

@Component
@Order(2)
@Primary
class PO_OrderedB_Primary : PO_Mix {
    override fun tag() = "B"
}

interface PO_Sortable {
    fun tag(): String
}

@Component
@Order(1)
class PO_OFirst : PO_Sortable {
    override fun tag() = "1"
}

@Component
@Order(2)
class PO_OSecond : PO_Sortable {
    override fun tag() = "2"
}

@Component
@Order(3)
class PO_OThird : PO_Sortable {
    override fun tag() = "3"
}

@Component
class PO_Unordered : PO_Sortable {
    override fun tag() = "unordered"
}

package top.wcpe.taboolib.ioc

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import top.wcpe.taboolib.ioc.annotation.*

/**
 * @Primary 和 @Order 注解测试
 */
class PrimaryAndOrderTest {

    // ==================== @Primary ====================

    @Test
    fun `Primary - 同类型多 Bean 时优先返回 @Primary 标记的`() {
        val ctx = IocTestContext()
        ctx.register(SecondaryImpl::class.java)
        ctx.register(PrimaryImpl::class.java)
        ctx.initialize()

        val bean = ctx.getBean(PrimaryTestInterface::class.java)
        assertNotNull(bean)
        assertEquals("primary", bean!!.tag())
    }

    @Test
    fun `Primary - 无 @Primary 时按 order 返回`() {
        val ctx = IocTestContext()
        ctx.register(OrderedImplB::class.java)
        ctx.register(OrderedImplA::class.java)
        ctx.initialize()

        val bean = ctx.getBean(OrderTestInterface::class.java)
        assertNotNull(bean)
        assertEquals("A", bean!!.tag(), "order 值小的应优先")
    }

    // ==================== @Order ====================

    @Test
    fun `Order - getBeansOfType 按 order 升序返回`() {
        val ctx = IocTestContext()
        ctx.register(OrderedImplB::class.java)
        ctx.register(OrderedImplA::class.java)
        ctx.register(OrderedImplC::class.java)
        ctx.initialize()

        val all = ctx.getBeansOfType(OrderTestInterface::class.java)
        assertEquals(3, all.size)
        assertEquals(listOf("A", "B", "C"), all.map { it.tag() })
    }

    @Test
    fun `Order - 未标注 @Order 的排在最后`() {
        val ctx = IocTestContext()
        ctx.register(OrderedImplA::class.java)
        ctx.register(NoOrderImpl::class.java)
        ctx.initialize()

        val all = ctx.getBeansOfType(OrderTestInterface::class.java)
        assertEquals(2, all.size)
        assertEquals("A", all[0].tag())
        assertEquals("none", all[1].tag())
    }

    // ==================== 测试用 Bean ====================

    interface PrimaryTestInterface {
        fun tag(): String
    }

    @Component("secondaryImpl")
    class SecondaryImpl : PrimaryTestInterface {
        override fun tag() = "secondary"
    }

    @Component("primaryImpl")
    @Primary
    class PrimaryImpl : PrimaryTestInterface {
        override fun tag() = "primary"
    }

    interface OrderTestInterface {
        fun tag(): String
    }

    @Component("orderedA")
    @Order(1)
    class OrderedImplA : OrderTestInterface {
        override fun tag() = "A"
    }

    @Component("orderedB")
    @Order(2)
    class OrderedImplB : OrderTestInterface {
        override fun tag() = "B"
    }

    @Component("orderedC")
    @Order(3)
    class OrderedImplC : OrderTestInterface {
        override fun tag() = "C"
    }

    @Component("noOrderImpl")
    class NoOrderImpl : OrderTestInterface {
        override fun tag() = "none"
    }
}

package top.wcpe.taboolib.ioc.test

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import top.wcpe.taboolib.ioc.IocTestContext
import top.wcpe.taboolib.ioc.annotation.*

/**
 * @Component / @Service / @Repository / @Controller 基础语义
 */
class ComponentAnnotationsTest {

    @Test
    fun `component default name is class name with first lower`() {
        val ctx = IocTestContext()
        ctx.register(CA_PlainComp::class.java)
        ctx.initialize()
        assertTrue(ctx.containsBean("CA_PlainComp".replaceFirstChar { it.lowercase() }))
    }

    @Test
    fun `service default name first lower`() {
        val ctx = IocTestContext()
        ctx.register(CA_MyService::class.java)
        ctx.initialize()
        assertTrue(ctx.containsBean("CA_MyService".replaceFirstChar { it.lowercase() }))
    }

    @Test
    fun `repository default name first lower`() {
        val ctx = IocTestContext()
        ctx.register(CA_MyRepo::class.java)
        ctx.initialize()
        assertTrue(ctx.containsBean("CA_MyRepo".replaceFirstChar { it.lowercase() }))
    }

    @Test
    fun `controller default name first lower`() {
        val ctx = IocTestContext()
        ctx.register(CA_MyCtrl::class.java)
        ctx.initialize()
        assertTrue(ctx.containsBean("CA_MyCtrl".replaceFirstChar { it.lowercase() }))
    }

    @Test
    fun `custom name on component should take effect`() {
        val ctx = IocTestContext()
        ctx.register(CA_NamedComp::class.java)
        ctx.initialize()
        assertTrue(ctx.containsBean("renamedComp"))
    }

    @Test
    fun `custom name on service should take effect`() {
        val ctx = IocTestContext()
        ctx.register(CA_NamedService::class.java)
        ctx.initialize()
        assertTrue(ctx.containsBean("renamedService"))
    }

    @Test
    fun `service and component should both produce single bean`() {
        val ctx = IocTestContext()
        ctx.register(CA_MyService::class.java)
        ctx.initialize()
        val list = ctx.getBeansOfType(CA_MyService::class.java)
        assertEquals(1, list.size)
    }

    @Test
    fun `service instances are singleton by default`() {
        val ctx = IocTestContext()
        ctx.register(CA_MyService::class.java)
        ctx.initialize()
        val a = ctx.getBean(CA_MyService::class.java)
        val b = ctx.getBean(CA_MyService::class.java)
        assertSame(a, b)
    }
}

@Component
class CA_PlainComp

@Service
class CA_MyService

@Repository
class CA_MyRepo

@Controller
class CA_MyCtrl

@Component("renamedComp")
class CA_NamedComp

@Service("renamedService")
class CA_NamedService

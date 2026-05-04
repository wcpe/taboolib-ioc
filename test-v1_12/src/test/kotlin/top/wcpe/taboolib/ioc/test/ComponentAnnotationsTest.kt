package top.wcpe.taboolib.ioc.test.components

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import top.wcpe.taboolib.ioc.IocTestContext
import top.wcpe.taboolib.ioc.annotation.*

class ComponentAnnotationsTest {

    @Test
    fun `component class is registered`() {
        val ctx = IocTestContext()
        ctx.register(CompC::class.java); ctx.initialize()
        assertNotNull(ctx.getBean(CompC::class.java))
    }

    @Test
    fun `service class is registered`() {
        val ctx = IocTestContext()
        ctx.register(ServS::class.java); ctx.initialize()
        assertNotNull(ctx.getBean(ServS::class.java))
    }

    @Test
    fun `repository class is registered`() {
        val ctx = IocTestContext()
        ctx.register(RepR::class.java); ctx.initialize()
        assertNotNull(ctx.getBean(RepR::class.java))
    }

    @Test
    fun `controller class is registered`() {
        val ctx = IocTestContext()
        ctx.register(CtlC::class.java); ctx.initialize()
        assertNotNull(ctx.getBean(CtlC::class.java))
    }

    @Test
    fun `configuration class is registered`() {
        val ctx = IocTestContext()
        ctx.register(CfgG::class.java); ctx.initialize()
        assertNotNull(ctx.getBean(CfgG::class.java))
    }

    @Test
    fun `aspect class registered even without Component`() {
        val ctx = IocTestContext()
        ctx.register(AspA::class.java); ctx.initialize()
        assertNotNull(ctx.getBean(AspA::class.java))
    }

    @Test
    fun `Component value sets bean name`() {
        val ctx = IocTestContext()
        ctx.register(NamedCompX::class.java); ctx.initialize()
        assertTrue(ctx.containsBean("xxx"))
    }

    @Test
    fun `default bean name is lowerCamel of simple name`() {
        val ctx = IocTestContext()
        ctx.register(CompC::class.java); ctx.initialize()
        assertTrue(ctx.containsBean("compC"))
    }
}

@Component class CompC
@Service class ServS
@Repository class RepR
@Controller class CtlC
@Configuration class CfgG
@Aspect class AspA
@Component("xxx") class NamedCompX

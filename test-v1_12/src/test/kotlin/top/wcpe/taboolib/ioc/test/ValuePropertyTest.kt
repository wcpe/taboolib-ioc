package top.wcpe.taboolib.ioc.test.value

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import top.wcpe.taboolib.ioc.IocTestContext
import top.wcpe.taboolib.ioc.annotation.*
import top.wcpe.taboolib.ioc.inject.ValueResolver

class ValuePropertyTest {

    @Test
    fun `Value with literal text`() {
        val ctx = IocTestContext()
        ctx.register(ValLiteral::class.java); ctx.initialize()
        assertEquals("literal", ctx.getBean(ValLiteral::class.java)!!.text)
    }

    @Test
    fun `Value with system property`() {
        System.setProperty("ioc.v12.x", "from-sys")
        try {
            val ctx = IocTestContext()
            ctx.register(ValSys::class.java); ctx.initialize()
            assertEquals("from-sys", ctx.getBean(ValSys::class.java)!!.text)
        } finally { System.clearProperty("ioc.v12.x") }
    }

    @Test
    fun `Value with default fallback`() {
        val ctx = IocTestContext()
        ctx.register(ValDefault::class.java); ctx.initialize()
        assertEquals("def", ctx.getBean(ValDefault::class.java)!!.text)
    }

    @Test
    fun `PropertySource loads from test properties`() {
        ValueResolver.clearProperties()
        val ctx = IocTestContext()
        ctx.register(ValConfig::class.java); ctx.initialize()
        val cfg = ctx.getBean(ValConfig::class.java)!!
        assertEquals("TabooLib IoC TestV12", cfg.title)
        assertEquals(8082, cfg.port)
    }

    @Test
    fun `Value bool conversion`() {
        ValueResolver.clearProperties()
        val ctx = IocTestContext()
        ctx.register(ValConfig::class.java); ctx.initialize()
        assertTrue(ctx.getBean(ValConfig::class.java)!!.enabled)
    }

    @Test
    fun `Value int conversion`() {
        ValueResolver.clearProperties()
        val ctx = IocTestContext()
        ctx.register(ValConfig::class.java); ctx.initialize()
        assertEquals(8, ctx.getBean(ValConfig::class.java)!!.threads)
    }
}

@Component
class ValLiteral {
    @Value("literal") var text: String = ""
}

@Component
class ValSys {
    @Value("\${ioc.v12.x}") var text: String = ""
}

@Component
class ValDefault {
    @Value("\${ioc.v12.missing:def}") var text: String = ""
}

@Configuration
@PropertySource("test.properties")
class ValConfig {
    @Value("\${app.title}") var title: String = ""
    @Value("\${app.port}") var port: Int = 0
    @Value("\${app.enabled}") var enabled: Boolean = false
    @Value("\${app.threads}") var threads: Int = 0
}

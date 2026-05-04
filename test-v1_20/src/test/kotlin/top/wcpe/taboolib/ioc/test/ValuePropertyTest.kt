package top.wcpe.taboolib.ioc.test

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import top.wcpe.taboolib.ioc.IocTestContext
import top.wcpe.taboolib.ioc.annotation.*

/**
 * @Value / @PropertySource 测试
 */
class ValuePropertyTest {

    @BeforeEach
    fun setUp() {
        System.clearProperty("ioc.vp.name")
        System.clearProperty("ioc.vp.count")
        System.clearProperty("ioc.vp.flag")
    }

    @AfterEach
    fun tearDown() {
        System.clearProperty("ioc.vp.name")
        System.clearProperty("ioc.vp.count")
        System.clearProperty("ioc.vp.flag")
    }

    @Test
    fun `value uses default when property missing`() {
        val ctx = IocTestContext()
        ctx.register(VP_DefaultBean::class.java)
        ctx.initialize()
        val bean = ctx.getBean(VP_DefaultBean::class.java)!!
        assertEquals("fallback", bean.name)
    }

    @Test
    fun `value reads system property when set`() {
        System.setProperty("ioc.vp.name", "actual")
        val ctx = IocTestContext()
        ctx.register(VP_DefaultBean::class.java)
        ctx.initialize()
        val bean = ctx.getBean(VP_DefaultBean::class.java)!!
        assertEquals("actual", bean.name)
    }

    @Test
    fun `value converts to int`() {
        System.setProperty("ioc.vp.count", "42")
        val ctx = IocTestContext()
        ctx.register(VP_IntBean::class.java)
        ctx.initialize()
        assertEquals(42, ctx.getBean(VP_IntBean::class.java)!!.count)
    }

    @Test
    fun `value converts to boolean`() {
        System.setProperty("ioc.vp.flag", "true")
        val ctx = IocTestContext()
        ctx.register(VP_BoolBean::class.java)
        ctx.initialize()
        assertTrue(ctx.getBean(VP_BoolBean::class.java)!!.flag)
    }

    @Test
    fun `value int uses default when missing`() {
        val ctx = IocTestContext()
        ctx.register(VP_IntBean::class.java)
        ctx.initialize()
        assertEquals(7, ctx.getBean(VP_IntBean::class.java)!!.count)
    }

    @Test
    fun `propertySource loads classpath properties`() {
        val ctx = IocTestContext()
        ctx.register(VP_PropConfig::class.java)
        ctx.register(VP_PropBean::class.java)
        ctx.initialize()
        val bean = ctx.getBean(VP_PropBean::class.java)!!
        assertEquals("hello-from-file", bean.greeting)
    }
}

@Component
class VP_DefaultBean {
    @Value("\${ioc.vp.name:fallback}")
    var name: String = ""
}

@Component
class VP_IntBean {
    @Value("\${ioc.vp.count:7}")
    var count: Int = 0
}

@Component
class VP_BoolBean {
    @Value("\${ioc.vp.flag:false}")
    var flag: Boolean = false
}

@Configuration
@PropertySource("test.properties")
class VP_PropConfig

@Component
class VP_PropBean {
    @Value("\${app.greeting:unset}")
    var greeting: String = ""
}

package top.wcpe.taboolib.ioc.test.registry

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import top.wcpe.taboolib.ioc.IocTestContext
import top.wcpe.taboolib.ioc.annotation.Component
import top.wcpe.taboolib.ioc.annotation.Inject
import top.wcpe.taboolib.ioc.annotation.Named

class BeanRegistryTest {

    @Test
    fun `register single component`() {
        val ctx = IocTestContext()
        ctx.register(RegA::class.java)
        ctx.initialize()
        assertNotNull(ctx.getBean(RegA::class.java))
    }

    @Test
    fun `register duplicate name should overwrite`() {
        val ctx = IocTestContext()
        ctx.register(RegA::class.java)
        ctx.register(RegA::class.java)
        ctx.initialize()
        assertEquals(1, ctx.getBeansOfType(RegA::class.java).size)
    }

    @Test
    fun `getBeansOfType returns all beans of type`() {
        val ctx = IocTestContext()
        ctx.register(RegA::class.java)
        ctx.register(RegB::class.java)
        ctx.initialize()
        val list = ctx.getBeansOfType(RegBase::class.java)
        assertEquals(2, list.size)
    }

    @Test
    fun `containsBean returns true for registered`() {
        val ctx = IocTestContext()
        ctx.register(RegA::class.java)
        ctx.initialize()
        assertTrue(ctx.containsBean("regA"))
    }

    @Test
    fun `containsBean returns false for missing`() {
        val ctx = IocTestContext()
        assertFalse(ctx.containsBean("missing"))
    }

    @Test
    fun `getBeanNames lists all`() {
        val ctx = IocTestContext()
        ctx.register(RegA::class.java)
        ctx.register(RegB::class.java)
        ctx.initialize()
        val names = ctx.getBeanNames()
        assertTrue(names.contains("regA"))
        assertTrue(names.contains("regB"))
    }

    @Test
    fun `getSingleton returns instance after init`() {
        val ctx = IocTestContext()
        ctx.register(RegA::class.java)
        ctx.initialize()
        assertNotNull(ctx.getSingleton("regA"))
    }

    @Test
    fun `registerBean adds manual bean`() {
        val ctx = IocTestContext()
        ctx.registerBean("manualToken", RegA())
        assertTrue(ctx.containsBean("manualToken"))
        assertNotNull(ctx.getBean(RegA::class.java, "manualToken"))
    }

    @Test
    fun `getBean by type and name returns correct bean`() {
        val ctx = IocTestContext()
        ctx.register(RegNamedA::class.java)
        ctx.register(RegNamedB::class.java)
        ctx.initialize()
        val a = ctx.getBean(RegBase::class.java, "alpha")
        val b = ctx.getBean(RegBase::class.java, "beta")
        assertEquals("alpha", (a as RegNamedA).label)
        assertEquals("beta", (b as RegNamedB).label)
    }

    @Test
    fun `register with custom Component name`() {
        val ctx = IocTestContext()
        ctx.register(RegNamedA::class.java)
        ctx.initialize()
        assertTrue(ctx.containsBean("alpha"))
    }

    @Test
    fun `getBean missing returns null`() {
        val ctx = IocTestContext()
        ctx.initialize()
        assertNull(ctx.getBean(RegA::class.java))
    }

    @Test
    fun `inject by Named picks correct bean`() {
        val ctx = IocTestContext()
        ctx.register(RegNamedA::class.java)
        ctx.register(RegNamedB::class.java)
        ctx.register(RegConsumer::class.java)
        ctx.initialize()
        val c = ctx.getBean(RegConsumer::class.java)!!
        assertEquals("beta", c.target.label)
    }

    @Test
    fun `getBeansOfType with no matches returns empty`() {
        val ctx = IocTestContext()
        ctx.initialize()
        assertTrue(ctx.getBeansOfType(RegA::class.java).isEmpty())
    }
}

interface RegBase
@Component
class RegA : RegBase
@Component
class RegB : RegBase

class RegNamedABase
@Component("alpha")
class RegNamedA : RegBase {
    val label = "alpha"
}

@Component("beta")
class RegNamedB : RegBase {
    val label = "beta"
}

@Component
class RegConsumer {
    @Inject
    @Named("beta")
    lateinit var target: RegNamedB
}

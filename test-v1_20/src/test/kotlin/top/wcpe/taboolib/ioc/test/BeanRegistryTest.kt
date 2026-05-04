package top.wcpe.taboolib.ioc.test

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import top.wcpe.taboolib.ioc.IocTestContext
import top.wcpe.taboolib.ioc.annotation.*

/**
 * Bean 注册与查询基础测试
 */
class BeanRegistryTest {

    @Test
    fun `register component should add bean definition`() {
        val ctx = IocTestContext()
        ctx.register(BR_FooService::class.java)
        ctx.initialize()
        assertTrue(ctx.containsBean("BR_FooService".replaceFirstChar { it.lowercase() }))
    }

    @Test
    fun `register duplicate should overwrite but not throw`() {
        val ctx = IocTestContext()
        ctx.register(BR_FooService::class.java)
        ctx.register(BR_FooService::class.java)
        ctx.initialize()
        assertNotNull(ctx.getBean(BR_FooService::class.java))
    }

    @Test
    fun `getBean by type should return registered bean`() {
        val ctx = IocTestContext()
        ctx.register(BR_FooService::class.java)
        ctx.initialize()
        val bean = ctx.getBean(BR_FooService::class.java)
        assertNotNull(bean)
    }

    @Test
    fun `getBeansOfType should return multiple implementations`() {
        val ctx = IocTestContext()
        ctx.register(BR_ImplA::class.java)
        ctx.register(BR_ImplB::class.java)
        ctx.initialize()
        val all = ctx.getBeansOfType(BR_Shape::class.java)
        assertEquals(2, all.size)
    }

    @Test
    fun `containsBean should return true for registered bean`() {
        val ctx = IocTestContext()
        ctx.register(BR_FooService::class.java)
        ctx.initialize()
        assertTrue(ctx.containsBean("BR_FooService".replaceFirstChar { it.lowercase() }))
    }

    @Test
    fun `containsBean should return false for unknown`() {
        val ctx = IocTestContext()
        ctx.initialize()
        assertFalse(ctx.containsBean("nonexistent"))
    }

    @Test
    fun `getBeanNames should include registered bean`() {
        val ctx = IocTestContext()
        ctx.register(BR_FooService::class.java)
        ctx.initialize()
        assertTrue(ctx.getBeanNames().any { it.equals("BR_FooService", ignoreCase = true) })
    }

    @Test
    fun `getSingleton should return initialized instance`() {
        val ctx = IocTestContext()
        ctx.register(BR_FooService::class.java)
        ctx.initialize()
        val name = "BR_FooService".replaceFirstChar { it.lowercase() }
        assertNotNull(ctx.getSingleton(name))
    }

    @Test
    fun `getBean for unregistered type should return null`() {
        val ctx = IocTestContext()
        ctx.initialize()
        assertNull(ctx.getBean(BR_FooService::class.java))
    }

    @Test
    fun `manual registerBean should be injectable`() {
        val ctx = IocTestContext()
        val manual = BR_FooService()
        ctx.registerBean("myFoo", manual)
        ctx.register(BR_ManualConsumer::class.java)
        ctx.initialize()
        val consumer = ctx.getBean(BR_ManualConsumer::class.java)
        assertNotNull(consumer)
        assertSame(manual, consumer!!.foo)
    }

    @Test
    fun `manual registerBean should be queryable by name`() {
        val ctx = IocTestContext()
        ctx.registerBean("hello", "world")
        ctx.initialize()
        assertTrue(ctx.containsBean("hello"))
        assertEquals("world", ctx.getBean(String::class.java, "hello"))
    }

    @Test
    fun `named lookup by string should return correct bean`() {
        val ctx = IocTestContext()
        ctx.register(BR_ImplA::class.java)
        ctx.register(BR_ImplB::class.java)
        ctx.initialize()
        val a = ctx.getBean(BR_Shape::class.java, "BR_ImplA".replaceFirstChar { it.lowercase() })
        val b = ctx.getBean(BR_Shape::class.java, "BR_ImplB".replaceFirstChar { it.lowercase() })
        assertEquals("A", a!!.tag())
        assertEquals("B", b!!.tag())
    }
}

// fixtures
@Service
class BR_FooService {
    fun hello() = "foo"
}

interface BR_Shape {
    fun tag(): String
}

@Component
class BR_ImplA : BR_Shape {
    override fun tag() = "A"
}

@Component
class BR_ImplB : BR_Shape {
    override fun tag() = "B"
}

@Component
class BR_ManualConsumer {
    @Inject
    @Named("myFoo")
    lateinit var foo: BR_FooService
}

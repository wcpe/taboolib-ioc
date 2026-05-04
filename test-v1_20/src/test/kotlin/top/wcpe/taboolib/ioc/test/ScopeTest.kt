package top.wcpe.taboolib.ioc.test

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import top.wcpe.taboolib.ioc.IocTestContext
import top.wcpe.taboolib.ioc.annotation.*
import top.wcpe.taboolib.ioc.bean.BeanDefinition
import top.wcpe.taboolib.ioc.bean.BeanScope
import java.util.concurrent.ConcurrentHashMap

/**
 * 作用域测试
 */
class ScopeTest {

    @Test
    fun `singleton is default`() {
        val ctx = IocTestContext()
        ctx.register(SC_Default::class.java)
        ctx.initialize()
        val a = ctx.getBean(SC_Default::class.java)
        val b = ctx.getBean(SC_Default::class.java)
        assertSame(a, b)
    }

    @Test
    fun `prototype produces different instance`() {
        SC_Proto.count = 0
        val ctx = IocTestContext()
        ctx.register(SC_Proto::class.java)
        ctx.initialize()
        val a = ctx.getBean(SC_Proto::class.java)
        val b = ctx.getBean(SC_Proto::class.java)
        assertNotSame(a, b)
    }

    @Test
    fun `prototype not initialized eagerly`() {
        SC_Proto.count = 0
        val ctx = IocTestContext()
        ctx.register(SC_Proto::class.java)
        ctx.initialize()
        assertEquals(0, SC_Proto.count)
    }

    @Test
    fun `prototype each access creates new`() {
        SC_Proto.count = 0
        val ctx = IocTestContext()
        ctx.register(SC_Proto::class.java)
        ctx.initialize()
        ctx.getBean(SC_Proto::class.java)
        ctx.getBean(SC_Proto::class.java)
        ctx.getBean(SC_Proto::class.java)
        assertEquals(3, SC_Proto.count)
    }

    @Test
    fun `custom scope returns same instance for same key`() {
        SC_SessionBean.count = 0
        val cache = ConcurrentHashMap<String, Any>()
        val ctx = IocTestContext()
        ctx.register(SC_SessionBean::class.java)
        ctx.registerScope("session") { name, _, creator ->
            cache.getOrPut(name) { creator() }
        }
        ctx.initialize()
        val a = ctx.getBean(SC_SessionBean::class.java)
        val b = ctx.getBean(SC_SessionBean::class.java)
        assertSame(a, b)
        assertEquals(1, SC_SessionBean.count)
    }

    @Test
    fun `custom scope creator only called once per request`() {
        var creatorCalls = 0
        val cache = ConcurrentHashMap<String, Any>()
        val ctx = IocTestContext()
        ctx.register(SC_SessionBean::class.java)
        ctx.registerScope("session") { name, _, creator ->
            cache.getOrPut(name) {
                creatorCalls++
                creator()
            }
        }
        ctx.initialize()
        ctx.getBean(SC_SessionBean::class.java)
        ctx.getBean(SC_SessionBean::class.java)
        assertEquals(1, creatorCalls)
    }

    @Test
    fun `custom scope can be cleared and creator reinvoked`() {
        var creatorCalls = 0
        val cache = ConcurrentHashMap<String, Any>()
        val scope = object : BeanScope {
            override fun get(name: String, definition: BeanDefinition, creator: () -> Any): Any {
                return cache.getOrPut(name) {
                    creatorCalls++
                    creator()
                }
            }

            override fun clear() {
                cache.clear()
            }
        }
        val ctx = IocTestContext()
        ctx.register(SC_SessionBean::class.java)
        ctx.registerScope("session", scope)
        ctx.initialize()
        ctx.getBean(SC_SessionBean::class.java)
        scope.clear()
        ctx.getBean(SC_SessionBean::class.java)
        assertEquals(2, creatorCalls)
    }

    @Test
    fun `thread scope same instance within thread`() {
        val ctx = IocTestContext()
        ctx.register(SC_ThreadBean::class.java)
        ctx.registerScope("thread", ThreadLocalScope())
        ctx.initialize()
        val a = ctx.getBean(SC_ThreadBean::class.java)
        val b = ctx.getBean(SC_ThreadBean::class.java)
        assertSame(a, b)
    }

    @Test
    fun `thread scope different across threads`() {
        val ctx = IocTestContext()
        ctx.register(SC_ThreadBean::class.java)
        ctx.registerScope("thread", ThreadLocalScope())
        ctx.initialize()
        val a = ctx.getBean(SC_ThreadBean::class.java)
        var b: Any? = null
        val t = Thread { b = ctx.getBean(SC_ThreadBean::class.java) }
        t.start()
        t.join()
        assertNotSame(a, b)
    }

    @Test
    fun `singleton bean is the same in different lookups by interface`() {
        val ctx = IocTestContext()
        ctx.register(SC_Default::class.java)
        ctx.initialize()
        val a = ctx.getBean(SC_Default::class.java)
        val b = ctx.getBean(SC_Default::class.java, "SC_Default".replaceFirstChar { it.lowercase() })
        assertSame(a, b)
    }
}

@Component
class SC_Default

@Component
@Prototype
class SC_Proto {
    companion object {
        var count = 0
    }

    init {
        count++
    }
}

@Component
@Scope("session")
class SC_SessionBean {
    companion object {
        var count = 0
    }

    init {
        count++
    }
}

@Component
@Scope("thread")
class SC_ThreadBean

private fun IocTestContext.registerScope(name: String, block: (String, BeanDefinition, () -> Any) -> Any) {
    this.registerScope(name, object : BeanScope {
        override fun get(beanName: String, definition: BeanDefinition, creator: () -> Any): Any {
            return block(beanName, definition, creator)
        }
    })
}

class ThreadLocalScope : BeanScope {
    private val tl = ThreadLocal.withInitial { ConcurrentHashMap<String, Any>() }
    override fun get(name: String, definition: BeanDefinition, creator: () -> Any): Any {
        return tl.get().getOrPut(name) { creator() }
    }
    override fun clear() {
        tl.remove()
    }
}

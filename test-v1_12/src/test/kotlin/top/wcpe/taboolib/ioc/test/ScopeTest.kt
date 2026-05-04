package top.wcpe.taboolib.ioc.test.scope

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import top.wcpe.taboolib.ioc.IocTestContext
import top.wcpe.taboolib.ioc.annotation.*
import top.wcpe.taboolib.ioc.bean.BeanDefinition
import top.wcpe.taboolib.ioc.bean.BeanScope
import top.wcpe.taboolib.ioc.bean.BeanScopes

class ScopeTest {

    @Test
    fun `singleton returns same instance`() {
        val ctx = IocTestContext()
        ctx.register(ScopeSingleton::class.java); ctx.initialize()
        val a = ctx.getBean(ScopeSingleton::class.java)
        val b = ctx.getBean(ScopeSingleton::class.java)
        assertSame(a, b)
    }

    @Test
    fun `prototype returns different instances`() {
        val ctx = IocTestContext()
        ctx.register(ScopeProto::class.java); ctx.initialize()
        val a = ctx.getBean(ScopeProto::class.java)
        val b = ctx.getBean(ScopeProto::class.java)
        assertNotSame(a, b)
    }

    @Test
    fun `prototype has prototype scope string`() {
        val ctx = IocTestContext()
        ctx.register(ScopeProto::class.java)
        val def = ctx.registry.getByName("scopeProto")
        assertEquals(BeanScopes.PROTOTYPE, def?.scope)
    }

    @Test
    fun `ThreadScope yields per-thread instances`() {
        val ctx = IocTestContext()
        ctx.registerScope(BeanScopes.THREAD, top.wcpe.taboolib.ioc.scope.ThreadBeanScope())
        ctx.register(ScopeThread::class.java); ctx.initialize()
        val main = ctx.getBean(ScopeThread::class.java)
        var other: ScopeThread? = null
        val t = Thread { other = ctx.getBean(ScopeThread::class.java) }
        t.start(); t.join()
        assertNotNull(main); assertNotNull(other)
        assertNotSame(main, other)
    }

    @Test
    fun `custom scope is invoked`() {
        val ctx = IocTestContext()
        val counting = CountingScope()
        ctx.registerScope("counting", counting)
        ctx.register(ScopeCustom::class.java); ctx.initialize()
        repeat(3) { ctx.getBean(ScopeCustom::class.java) }
        assertTrue(counting.hits >= 3)
    }

    @Test
    fun `singleton scope string`() {
        val ctx = IocTestContext()
        ctx.register(ScopeSingleton::class.java)
        assertEquals(BeanScopes.SINGLETON, ctx.registry.getByName("scopeSingleton")?.scope)
    }

    @Test
    fun `ThreadScope annotation normalized scope`() {
        val ctx = IocTestContext()
        ctx.register(ScopeThread::class.java)
        assertEquals(BeanScopes.THREAD, ctx.registry.getByName("scopeThread")?.scope)
    }

    @Test
    fun `RefreshScope annotation normalized scope`() {
        val ctx = IocTestContext()
        ctx.register(ScopeRefresh::class.java)
        assertEquals(BeanScopes.REFRESH, ctx.registry.getByName("scopeRefresh")?.scope)
    }

    @Test
    fun `prototype singleton differ across calls`() {
        val ctx = IocTestContext()
        ctx.register(ScopeProto::class.java); ctx.initialize()
        val instances = (1..5).map { ctx.getBean(ScopeProto::class.java) }
        assertEquals(5, instances.toSet().size)
    }

    @Test
    fun `Scope annotation explicit singleton`() {
        val ctx = IocTestContext()
        ctx.register(ScopeExplicit::class.java); ctx.initialize()
        assertSame(ctx.getBean(ScopeExplicit::class.java), ctx.getBean(ScopeExplicit::class.java))
    }
}

@Component class ScopeSingleton
@Prototype @Component class ScopeProto
@ThreadScope @Component class ScopeThread
@RefreshScope @Component class ScopeRefresh
@Scope("singleton") @Component class ScopeExplicit
@Scope("counting") @Component class ScopeCustom

class CountingScope : BeanScope {
    var hits = 0
    private var cached: Any? = null
    override fun get(name: String, definition: BeanDefinition, creator: () -> Any): Any {
        hits++
        if (cached == null) cached = creator()
        return cached!!
    }
}

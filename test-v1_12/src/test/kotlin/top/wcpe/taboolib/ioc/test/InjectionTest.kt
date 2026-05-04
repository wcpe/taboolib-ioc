package top.wcpe.taboolib.ioc.test.injection

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import top.wcpe.taboolib.ioc.IocTestContext
import top.wcpe.taboolib.ioc.annotation.*

class InjectionTest {

    @Test
    fun `field injection works`() {
        val ctx = IocTestContext()
        ctx.register(InjA::class.java); ctx.register(InjB::class.java); ctx.initialize()
        assertEquals("a", ctx.getBean(InjB::class.java)!!.a.name())
    }

    @Test
    fun `constructor injection works`() {
        val ctx = IocTestContext()
        ctx.register(InjA::class.java); ctx.register(InjCtor::class.java); ctx.initialize()
        assertEquals("a", ctx.getBean(InjCtor::class.java)!!.a.name())
    }

    @Test
    fun `method injection works`() {
        val ctx = IocTestContext()
        ctx.register(InjA::class.java); ctx.register(InjMethod::class.java); ctx.initialize()
        assertEquals("a", ctx.getBean(InjMethod::class.java)!!.a.name())
    }

    @Test
    fun `Named picks specific bean`() {
        val ctx = IocTestContext()
        ctx.register(InjAlpha::class.java); ctx.register(InjBeta::class.java); ctx.register(InjNamedCons::class.java)
        ctx.initialize()
        val c = ctx.getBean(InjNamedCons::class.java)!!
        assertEquals("beta", c.chosen.name())
    }

    @Test
    fun `Resource with name picks specific bean`() {
        val ctx = IocTestContext()
        ctx.register(InjAlpha::class.java); ctx.register(InjBeta::class.java); ctx.register(InjResourceCons::class.java)
        ctx.initialize()
        assertEquals("alpha", ctx.getBean(InjResourceCons::class.java)!!.chosen.name())
    }

    @Test
    fun `Inject required false allows missing`() {
        val ctx = IocTestContext()
        ctx.register(InjOptional::class.java); ctx.initialize()
        val b = ctx.getBean(InjOptional::class.java)!!
        assertNull(b.missing)
    }

    @Test
    fun `Inject required true throws when missing`() {
        val ctx = IocTestContext()
        ctx.register(InjRequired::class.java)
        assertThrows(Exception::class.java) { ctx.initialize() }
    }

    @Test
    fun `Lazy field injection returns proxy and resolves on call`() {
        val ctx = IocTestContext()
        ctx.register(InjLazyTarget::class.java); ctx.register(InjLazyCons::class.java); ctx.initialize()
        val cons = ctx.getBean(InjLazyCons::class.java)!!
        assertEquals("lazy", cons.target.name())
    }

    @Test
    fun `constructor injection with Named`() {
        val ctx = IocTestContext()
        ctx.register(InjAlpha::class.java); ctx.register(InjBeta::class.java); ctx.register(InjCtorNamed::class.java)
        ctx.initialize()
        assertEquals("alpha", ctx.getBean(InjCtorNamed::class.java)!!.target.name())
    }

    @Test
    fun `multi-field injection works`() {
        val ctx = IocTestContext()
        ctx.register(InjA::class.java); ctx.register(InjAlpha::class.java); ctx.register(InjMulti::class.java)
        ctx.initialize()
        val b = ctx.getBean(InjMulti::class.java)!!
        assertEquals("a", b.a.name())
        assertEquals("alpha", b.aa.name())
    }

    @Test
    fun `setter method with multiple params`() {
        val ctx = IocTestContext()
        ctx.register(InjA::class.java); ctx.register(InjAlpha::class.java); ctx.register(InjMultiMethod::class.java)
        ctx.initialize()
        val b = ctx.getBean(InjMultiMethod::class.java)!!
        assertEquals("a|alpha", b.combined())
    }

    @Test
    fun `Named ctor picks correctly`() {
        val ctx = IocTestContext()
        ctx.register(InjAlpha::class.java); ctx.register(InjBeta::class.java); ctx.register(InjCtorBeta::class.java)
        ctx.initialize()
        assertEquals("beta", ctx.getBean(InjCtorBeta::class.java)!!.target.name())
    }

    @Test
    fun `getBean without registration returns null`() {
        val ctx = IocTestContext(); ctx.initialize()
        assertNull(ctx.getBean(InjA::class.java))
    }

    @Test
    fun `manualBean is injectable by name`() {
        val ctx = IocTestContext()
        ctx.registerBean("manualA", InjA())
        val a = ctx.getBean(InjA::class.java, "manualA")
        assertNotNull(a)
    }
}

interface InjNamed { fun name(): String }

@Component class InjA : InjNamed { override fun name() = "a" }
@Component class InjB {
    @Inject lateinit var a: InjA
}
@Component class InjCtor @Inject constructor(val a: InjA)
@Component class InjMethod {
    lateinit var a: InjA
    @Inject fun assign(a: InjA) { this.a = a }
}
@Component("alpha") class InjAlpha : InjNamed { override fun name() = "alpha" }
@Component("beta") class InjBeta : InjNamed { override fun name() = "beta" }

@Component class InjNamedCons {
    @Inject @Named("beta") lateinit var chosen: InjNamed
}

@Component class InjResourceCons {
    @Resource(name = "alpha") lateinit var chosen: InjNamed
}

@Component class InjOptional {
    @Inject(required = false) var missing: InjMissingType? = null
}

interface InjMissingType

@Component class InjRequired {
    @Inject lateinit var missing: InjMissingType
}

interface InjLazyApi { fun name(): String }
@Component class InjLazyTarget : InjLazyApi { override fun name() = "lazy" }
@Component class InjLazyCons {
    @Inject @Lazy lateinit var target: InjLazyApi
}

@Component class InjCtorNamed @Inject constructor(@Named("alpha") val target: InjNamed)
@Component class InjCtorBeta @Inject constructor(@Named("beta") val target: InjNamed)

@Component class InjMulti {
    @Inject lateinit var a: InjA
    @Inject @Named("alpha") lateinit var aa: InjNamed
}

@Component class InjMultiMethod {
    private lateinit var a: InjA
    private lateinit var b: InjNamed
    @Inject fun init(a: InjA, @Named("alpha") b: InjNamed) { this.a = a; this.b = b }
    fun combined() = "${a.name()}|${b.name()}"
}

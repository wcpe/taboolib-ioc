package top.wcpe.taboolib.ioc.test

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import top.wcpe.taboolib.ioc.IocTestContext
import top.wcpe.taboolib.ioc.annotation.*

/**
 * 依赖注入形式测试：构造、字段、方法、@Named、@Resource、@Inject(required=false)、@Lazy 等
 */
class InjectionTest {

    @Test
    fun `field inject works`() {
        val ctx = IocTestContext()
        ctx.register(IJ_Dep::class.java)
        ctx.register(IJ_FieldInjected::class.java)
        ctx.initialize()
        val b = ctx.getBean(IJ_FieldInjected::class.java)!!
        assertEquals("dep", b.dep.value())
    }

    @Test
    fun `constructor inject works`() {
        val ctx = IocTestContext()
        ctx.register(IJ_Dep::class.java)
        ctx.register(IJ_CtorInjected::class.java)
        ctx.initialize()
        val b = ctx.getBean(IJ_CtorInjected::class.java)!!
        assertEquals("dep", b.dep.value())
    }

    @Test
    fun `method inject works`() {
        val ctx = IocTestContext()
        ctx.register(IJ_Dep::class.java)
        ctx.register(IJ_MethodInjected::class.java)
        ctx.initialize()
        val b = ctx.getBean(IJ_MethodInjected::class.java)!!
        assertEquals("dep", b.value())
    }

    @Test
    fun `named selects correct implementation`() {
        val ctx = IocTestContext()
        ctx.register(IJ_AImpl::class.java)
        ctx.register(IJ_BImpl::class.java)
        ctx.register(IJ_NamedConsumer::class.java)
        ctx.initialize()
        val c = ctx.getBean(IJ_NamedConsumer::class.java)!!
        assertEquals("A", c.a.tag())
        assertEquals("B", c.b.tag())
    }

    @Test
    fun `resource name selects correct implementation`() {
        val ctx = IocTestContext()
        ctx.register(IJ_AImpl::class.java)
        ctx.register(IJ_BImpl::class.java)
        ctx.register(IJ_ResourceConsumer::class.java)
        ctx.initialize()
        val c = ctx.getBean(IJ_ResourceConsumer::class.java)!!
        assertEquals("B", c.shape.tag())
    }

    @Test
    fun `optional inject leaves field null when missing`() {
        val ctx = IocTestContext()
        ctx.register(IJ_OptionalConsumer::class.java)
        ctx.initialize()
        val c = ctx.getBean(IJ_OptionalConsumer::class.java)!!
        assertNull(c.dep)
    }

    @Test
    fun `optional inject populates field when present`() {
        val ctx = IocTestContext()
        ctx.register(IJ_Dep::class.java)
        ctx.register(IJ_OptionalConsumer::class.java)
        ctx.initialize()
        val c = ctx.getBean(IJ_OptionalConsumer::class.java)!!
        assertNotNull(c.dep)
    }

    @Test
    fun `required missing dependency should throw`() {
        val ctx = IocTestContext()
        ctx.register(IJ_FieldInjected::class.java)
        assertThrows(Exception::class.java) { ctx.initialize() }
    }

    @Test
    fun `lazy field returns proxy accessible on demand`() {
        val ctx = IocTestContext()
        ctx.register(IJ_LazyDepImpl::class.java)
        ctx.register(IJ_LazyConsumer::class.java)
        ctx.initialize()
        val c = ctx.getBean(IJ_LazyConsumer::class.java)!!
        assertNotNull(c.lazyDep)
        assertEquals("lazy", c.lazyDep.tag())
    }

    @Test
    fun `multi-layer dependency chain works`() {
        val ctx = IocTestContext()
        ctx.register(IJ_Dep::class.java)
        ctx.register(IJ_FieldInjected::class.java)
        ctx.register(IJ_Layer3::class.java)
        ctx.initialize()
        val l3 = ctx.getBean(IJ_Layer3::class.java)!!
        assertEquals("dep", l3.inner.dep.value())
    }

    @Test
    fun `circular field dependency resolves`() {
        val ctx = IocTestContext()
        ctx.register(IJ_CycleA::class.java)
        ctx.register(IJ_CycleB::class.java)
        ctx.initialize()
        val a = ctx.getBean(IJ_CycleA::class.java)!!
        val b = ctx.getBean(IJ_CycleB::class.java)!!
        assertSame(b, a.b)
        assertSame(a, b.a)
    }

    @Test
    fun `nested inject propagates dependencies`() {
        val ctx = IocTestContext()
        ctx.register(IJ_Dep::class.java)
        ctx.register(IJ_CtorInjected::class.java)
        ctx.register(IJ_NestedCtor::class.java)
        ctx.initialize()
        val n = ctx.getBean(IJ_NestedCtor::class.java)!!
        assertEquals("dep", n.c.dep.value())
    }

    @Test
    fun `named on interface type resolves by name`() {
        val ctx = IocTestContext()
        ctx.register(IJ_AImpl::class.java)
        ctx.register(IJ_BImpl::class.java)
        ctx.initialize()
        val a = ctx.getBean(IJ_Shape::class.java, "IJ_AImpl".replaceFirstChar { it.lowercase() })
        assertEquals("A", a!!.tag())
    }

    @Test
    fun `getBeansOfType returns interface implementations`() {
        val ctx = IocTestContext()
        ctx.register(IJ_AImpl::class.java)
        ctx.register(IJ_BImpl::class.java)
        ctx.initialize()
        assertEquals(2, ctx.getBeansOfType(IJ_Shape::class.java).size)
    }
}

// fixtures
@Component
class IJ_Dep {
    fun value() = "dep"
}

@Component
class IJ_FieldInjected {
    @Inject
    lateinit var dep: IJ_Dep
}

@Component
class IJ_CtorInjected @Inject constructor(val dep: IJ_Dep)

@Component
class IJ_MethodInjected {
    private lateinit var dep: IJ_Dep

    @Inject
    fun setDep(dep: IJ_Dep) {
        this.dep = dep
    }

    fun value() = dep.value()
}

interface IJ_Shape {
    fun tag(): String
}

@Component
class IJ_AImpl : IJ_Shape {
    override fun tag() = "A"
}

@Component
class IJ_BImpl : IJ_Shape {
    override fun tag() = "B"
}

@Component
class IJ_NamedConsumer {
    @Inject
    @Named("iJ_AImpl")
    lateinit var a: IJ_Shape

    @Inject
    @Named("iJ_BImpl")
    lateinit var b: IJ_Shape
}
@Component
class IJ_ResourceConsumer {
    @Resource(name = "iJ_BImpl")
    lateinit var shape: IJ_Shape
}

@Component
class IJ_OptionalConsumer {
    @Inject(required = false)
    var dep: IJ_Dep? = null
}

interface IJ_LazyDep {
    fun tag(): String
}

@Component
class IJ_LazyDepImpl : IJ_LazyDep {
    override fun tag() = "lazy"
}

@Component
class IJ_LazyConsumer {
    @Inject
    @Lazy
    lateinit var lazyDep: IJ_LazyDep
}

@Component
class IJ_Layer3 {
    @Inject
    lateinit var inner: IJ_FieldInjected
}

@Component
class IJ_NestedCtor @Inject constructor(val c: IJ_CtorInjected)

@Component
class IJ_CycleA {
    @Inject
    lateinit var b: IJ_CycleB
}

@Component
class IJ_CycleB {
    @Inject
    lateinit var a: IJ_CycleA
}

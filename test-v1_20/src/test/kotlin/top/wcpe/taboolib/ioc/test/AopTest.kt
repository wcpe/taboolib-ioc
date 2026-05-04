package top.wcpe.taboolib.ioc.test

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import top.wcpe.taboolib.ioc.IocTestContext
import top.wcpe.taboolib.ioc.annotation.*
import top.wcpe.taboolib.ioc.bean.MethodInvocation

/**
 * AOP 行为测试（@Before/@After/@Around/@Pointcut/@Order）
 */
class AopTest {

    @Test
    fun `before advice fires`() {
        AOP_BeforeAspect.calls = 0
        val ctx = IocTestContext()
        ctx.register(AOP_BeforeAspect::class.java)
        ctx.register(AOP_FooImpl::class.java)
        ctx.initialize()
        val foo = ctx.getBean(AOP_Foo::class.java)!!
        foo.doIt()
        assertEquals(1, AOP_BeforeAspect.calls)
    }

    @Test
    fun `after advice fires after method`() {
        AOP_AfterAspect.calls = 0
        val ctx = IocTestContext()
        ctx.register(AOP_AfterAspect::class.java)
        ctx.register(AOP_FooImpl::class.java)
        ctx.initialize()
        val foo = ctx.getBean(AOP_Foo::class.java)!!
        foo.doIt()
        assertEquals(1, AOP_AfterAspect.calls)
    }

    @Test
    fun `around can modify return value`() {
        val ctx = IocTestContext()
        ctx.register(AOP_AroundReturnAspect::class.java)
        ctx.register(AOP_FooImpl::class.java)
        ctx.initialize()
        val foo = ctx.getBean(AOP_Foo::class.java)!!
        assertEquals("intercepted", foo.doIt())
    }

    @Test
    fun `around can read arguments`() {
        AOP_AroundArgsAspect.lastArgs = null
        val ctx = IocTestContext()
        ctx.register(AOP_AroundArgsAspect::class.java)
        ctx.register(AOP_BarImpl::class.java)
        ctx.initialize()
        val bar = ctx.getBean(AOP_Bar::class.java)!!
        bar.echo("hello")
        assertNotNull(AOP_AroundArgsAspect.lastArgs)
        assertEquals("hello", AOP_AroundArgsAspect.lastArgs!![0])
    }

    @Test
    fun `multiple aspects ordered`() {
        AOP_OrderLog.log.clear()
        val ctx = IocTestContext()
        ctx.register(AOP_FirstAspect::class.java)
        ctx.register(AOP_SecondAspect::class.java)
        ctx.register(AOP_FooImpl::class.java)
        ctx.initialize()
        ctx.getBean(AOP_Foo::class.java)!!.doIt()
        assertTrue(AOP_OrderLog.log.contains("first"))
        assertTrue(AOP_OrderLog.log.contains("second"))
    }

    @Test
    fun `pointcut reused`() {
        AOP_PointcutAspect.calls = 0
        val ctx = IocTestContext()
        ctx.register(AOP_PointcutAspect::class.java)
        ctx.register(AOP_FooImpl::class.java)
        ctx.initialize()
        ctx.getBean(AOP_Foo::class.java)!!.doIt()
        assertEquals(1, AOP_PointcutAspect.calls)
    }

    @Test
    fun `non-matching method not advised`() {
        AOP_BeforeAspect.calls = 0
        val ctx = IocTestContext()
        ctx.register(AOP_BeforeAspect::class.java)
        ctx.register(AOP_BarImpl::class.java)
        ctx.initialize()
        ctx.getBean(AOP_Bar::class.java)!!.echo("x")
        assertEquals(0, AOP_BeforeAspect.calls)
    }

    @Test
    fun `aspect applies to component bean`() {
        AOP_BeforeAspect.calls = 0
        val ctx = IocTestContext()
        ctx.register(AOP_BeforeAspect::class.java)
        ctx.register(AOP_FooImpl::class.java)
        ctx.initialize()
        ctx.getBean(AOP_Foo::class.java)!!.doIt()
        assertTrue(AOP_BeforeAspect.calls > 0)
    }

    @Test
    fun `around exception propagates`() {
        val ctx = IocTestContext()
        ctx.register(AOP_PassthroughAspect::class.java)
        ctx.register(AOP_BlowImpl::class.java)
        ctx.initialize()
        val blow = ctx.getBean(AOP_Blow::class.java)!!
        val ex = assertThrows(Throwable::class.java) { blow.bang() }
        // 异常可能被层层包装；遍历 cause 链查找 kaboom
        val messages = generateSequence<Throwable>(ex) { it.cause }.mapNotNull { it.message }.toList()
        assertTrue(messages.any { it.contains("kaboom") }, "expected kaboom in chain: $messages")
    }

    @Test
    fun `proceed multiple times invokes target multiple times`() {
        AOP_FooImpl.invokeCount = 0
        val ctx = IocTestContext()
        ctx.register(AOP_DoubleAspect::class.java)
        ctx.register(AOP_FooImpl::class.java)
        ctx.initialize()
        ctx.getBean(AOP_Foo::class.java)!!.doIt()
        assertEquals(2, AOP_FooImpl.invokeCount)
    }

    @Test
    fun `around without proceed swallows result`() {
        val ctx = IocTestContext()
        ctx.register(AOP_NoProceedAspect::class.java)
        ctx.register(AOP_FooImpl::class.java)
        ctx.initialize()
        val r = ctx.getBean(AOP_Foo::class.java)!!.doIt()
        assertEquals("blocked", r)
    }

    @Test
    fun `wildcard expression matches`() {
        AOP_WildcardAspect.calls = 0
        val ctx = IocTestContext()
        ctx.register(AOP_WildcardAspect::class.java)
        ctx.register(AOP_FooImpl::class.java)
        ctx.initialize()
        ctx.getBean(AOP_Foo::class.java)!!.doIt()
        assertTrue(AOP_WildcardAspect.calls > 0)
    }

    @Test
    fun `aspect not affecting unrelated beans`() {
        AOP_BeforeAspect.calls = 0
        val ctx = IocTestContext()
        ctx.register(AOP_BeforeAspect::class.java)
        ctx.register(AOP_FooImpl::class.java)
        ctx.register(AOP_BarImpl::class.java)
        ctx.initialize()
        ctx.getBean(AOP_Bar::class.java)!!.echo("hi")
        assertEquals(0, AOP_BeforeAspect.calls)
    }

    @Test
    fun `before and around both fire`() {
        AOP_BeforeAspect.calls = 0
        val ctx = IocTestContext()
        ctx.register(AOP_BeforeAspect::class.java)
        ctx.register(AOP_AroundReturnAspect::class.java)
        ctx.register(AOP_FooImpl::class.java)
        ctx.initialize()
        val r = ctx.getBean(AOP_Foo::class.java)!!.doIt()
        assertEquals("intercepted", r)
        assertEquals(1, AOP_BeforeAspect.calls)
    }

    @Test
    fun `around chain via multiple advisors works`() {
        val ctx = IocTestContext()
        ctx.register(AOP_PrependAspect::class.java)
        ctx.register(AOP_AppendAspect::class.java)
        ctx.register(AOP_FooImpl::class.java)
        ctx.initialize()
        val r = ctx.getBean(AOP_Foo::class.java)!!.doIt()
        assertTrue(r.toString().contains("ok"))
    }

    @Test
    fun `around can return null`() {
        val ctx = IocTestContext()
        ctx.register(AOP_NullAspect::class.java)
        ctx.register(AOP_FooImpl::class.java)
        ctx.initialize()
        val r = ctx.getBean(AOP_Foo::class.java)!!.doIt()
        assertNull(r)
    }
}

interface AOP_Foo {
    fun doIt(): String?
}

@Component
class AOP_FooImpl : AOP_Foo {
    companion object {
        var invokeCount = 0
    }

    override fun doIt(): String {
        invokeCount++
        return "ok"
    }
}

interface AOP_Bar {
    fun echo(s: String): String
}

@Component
class AOP_BarImpl : AOP_Bar {
    override fun echo(s: String) = s
}

interface AOP_Blow {
    fun bang()
}

@Component
class AOP_BlowImpl : AOP_Blow {
    override fun bang() {
        throw RuntimeException("kaboom")
    }
}

@Aspect
class AOP_BeforeAspect {
    companion object {
        var calls = 0
    }

    @Before("execution(AOP_FooImpl.doIt)")
    fun before() {
        calls++
    }
}

@Aspect
class AOP_AfterAspect {
    companion object {
        var calls = 0
    }

    @After("execution(AOP_FooImpl.doIt)")
    fun after() {
        calls++
    }
}

@Aspect
class AOP_AroundReturnAspect {
    @Around("execution(AOP_FooImpl.doIt)")
    fun around(invocation: MethodInvocation): Any? {
        invocation.proceed()
        return "intercepted"
    }
}

@Aspect
class AOP_AroundArgsAspect {
    companion object {
        var lastArgs: Array<out Any?>? = null
    }

    @Around("execution(AOP_BarImpl.echo)")
    fun around(invocation: MethodInvocation): Any? {
        lastArgs = invocation.arguments
        return invocation.proceed()
    }
}

object AOP_OrderLog {
    val log = mutableListOf<String>()
}

@Aspect
@Order(1)
class AOP_FirstAspect {
    @Around("execution(AOP_FooImpl.doIt)")
    fun around(invocation: MethodInvocation): Any? {
        AOP_OrderLog.log.add("first")
        return invocation.proceed()
    }
}

@Aspect
@Order(2)
class AOP_SecondAspect {
    @Around("execution(AOP_FooImpl.doIt)")
    fun around(invocation: MethodInvocation): Any? {
        AOP_OrderLog.log.add("second")
        return invocation.proceed()
    }
}

@Aspect
class AOP_PointcutAspect {
    companion object {
        var calls = 0
    }

    @Pointcut("execution(AOP_FooImpl.doIt)")
    fun fooDo() {
    }

    @Before("fooDo")
    fun before() {
        calls++
    }
}

@Aspect
class AOP_PassthroughAspect {
    @Around("execution(AOP_BlowImpl.bang)")
    fun around(invocation: MethodInvocation): Any? = invocation.proceed()
}

@Aspect
class AOP_DoubleAspect {
    @Around("execution(AOP_FooImpl.doIt)")
    fun around(invocation: MethodInvocation): Any? {
        invocation.proceed()
        return invocation.proceed()
    }
}

@Aspect
class AOP_NoProceedAspect {
    @Around("execution(AOP_FooImpl.doIt)")
    fun around(invocation: MethodInvocation): Any? {
        return "blocked"
    }
}

@Aspect
class AOP_WildcardAspect {
    companion object {
        var calls = 0
    }

    @Around("execution(AOP_FooImpl.*)")
    fun around(invocation: MethodInvocation): Any? {
        calls++
        return invocation.proceed()
    }
}

@Aspect
@Order(1)
class AOP_PrependAspect {
    @Around("execution(AOP_FooImpl.doIt)")
    fun around(invocation: MethodInvocation): Any? {
        return "pre-${invocation.proceed()}"
    }
}

@Aspect
@Order(2)
class AOP_AppendAspect {
    @Around("execution(AOP_FooImpl.doIt)")
    fun around(invocation: MethodInvocation): Any? {
        return "${invocation.proceed()}-post"
    }
}

@Aspect
class AOP_NullAspect {
    @Around("execution(AOP_FooImpl.doIt)")
    fun around(invocation: MethodInvocation): Any? {
        invocation.proceed()
        return null
    }
}

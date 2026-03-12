package top.wcpe.taboolib.ioc.aop

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import top.wcpe.taboolib.ioc.IocTestContext
import top.wcpe.taboolib.ioc.annotation.*
import top.wcpe.taboolib.ioc.bean.MethodInvocation
import top.wcpe.taboolib.ioc.bean.PointcutExpression

class AopTest {

    // ── PointcutExpression 解析测试 ──

    @Test
    fun `pointcut should match exact class and method`() {
        val pc = PointcutExpression.parse("execution(AopTargetServiceImpl.doWork)")
        val method = AopTargetServiceImpl::class.java.getMethod("doWork")
        assertTrue(pc.matches(AopTargetServiceImpl::class.java, method))
    }

    @Test
    fun `pointcut should match wildcard method`() {
        val pc = PointcutExpression.parse("execution(AopTargetServiceImpl.*)")
        val method = AopTargetServiceImpl::class.java.getMethod("doWork")
        assertTrue(pc.matches(AopTargetServiceImpl::class.java, method))
    }

    @Test
    fun `pointcut should match wildcard class`() {
        val pc = PointcutExpression.parse("execution(*.doWork)")
        val method = AopTargetServiceImpl::class.java.getMethod("doWork")
        assertTrue(pc.matches(AopTargetServiceImpl::class.java, method))
    }

    @Test
    fun `pointcut should not match different method`() {
        val pc = PointcutExpression.parse("execution(AopTargetServiceImpl.otherMethod)")
        val method = AopTargetServiceImpl::class.java.getMethod("doWork")
        assertFalse(pc.matches(AopTargetServiceImpl::class.java, method))
    }

    // ── AspectScanner 测试 ──

    @Test
    fun `aspect scanner should parse before after around advisors`() {
        val aspect = TestLoggingAspect()
        val advisors = AspectScanner.scan(aspect, TestLoggingAspect::class.java)
        assertEquals(3, advisors.size)
    }

    // ── AOP 代理集成测试 ──

    @Test
    fun `before advice should be called before target method`() {
        TestLoggingAspect.callLog.clear()

        val ctx = IocTestContext()
        ctx.register(TestLoggingAspect::class.java)
        ctx.register(AopTargetServiceImpl::class.java)
        ctx.initialize()

        val service = ctx.getBean(AopTargetService::class.java)
        assertNotNull(service)
        val result = service!!.doWork()

        assertEquals("done", result)
        assertTrue(TestLoggingAspect.callLog.contains("before:doWork"))
    }

    @Test
    fun `after advice should be called after target method`() {
        TestLoggingAspect.callLog.clear()

        val ctx = IocTestContext()
        ctx.register(TestLoggingAspect::class.java)
        ctx.register(AopTargetServiceImpl::class.java)
        ctx.initialize()

        val service = ctx.getBean(AopTargetService::class.java)
        assertNotNull(service)
        service!!.doWork()

        assertTrue(TestLoggingAspect.callLog.contains("after:doWork"))
        // after 应在 before 之后
        val beforeIdx = TestLoggingAspect.callLog.indexOf("before:doWork")
        val afterIdx = TestLoggingAspect.callLog.indexOf("after:doWork")
        assertTrue(afterIdx > beforeIdx)
    }

    @Test
    fun `around advice should wrap target method`() {
        TestLoggingAspect.callLog.clear()

        val ctx = IocTestContext()
        ctx.register(TestLoggingAspect::class.java)
        ctx.register(AopTargetServiceImpl::class.java)
        ctx.initialize()

        val service = ctx.getBean(AopTargetService::class.java)
        assertNotNull(service)
        service!!.doWork()

        assertTrue(TestLoggingAspect.callLog.contains("around:before"))
        assertTrue(TestLoggingAspect.callLog.contains("around:after"))
    }

    @Test
    fun `bean without interface should not be proxied`() {
        val ctx = IocTestContext()
        ctx.register(TestLoggingAspect::class.java)
        ctx.register(NoInterfaceBean::class.java)
        ctx.initialize()

        val bean = ctx.getBean(NoInterfaceBean::class.java)
        assertNotNull(bean)
        // 没有接口，不会被代理，直接返回原始实例
        assertFalse(java.lang.reflect.Proxy.isProxyClass(bean!!.javaClass))
    }

    @Test
    fun `proxy should be JDK dynamic proxy for interface bean`() {
        TestLoggingAspect.callLog.clear()

        val ctx = IocTestContext()
        ctx.register(TestLoggingAspect::class.java)
        ctx.register(AopTargetServiceImpl::class.java)
        ctx.initialize()

        val service = ctx.getBean(AopTargetService::class.java)
        assertNotNull(service)
        assertTrue(java.lang.reflect.Proxy.isProxyClass(service!!.javaClass))
    }

    @Test
    fun `aspect bean itself should not be proxied`() {
        val ctx = IocTestContext()
        ctx.register(TestLoggingAspect::class.java)
        ctx.register(AopTargetServiceImpl::class.java)
        ctx.initialize()

        val aspect = ctx.getBean(TestLoggingAspect::class.java)
        assertNotNull(aspect)
        assertFalse(java.lang.reflect.Proxy.isProxyClass(aspect!!.javaClass))
    }

    // ── PointcutExpression 边界测试 ──

    @Test
    fun `pointcut should match package wildcard`() {
        val pc = PointcutExpression.parse("execution(top.wcpe.taboolib.ioc.aop..*.doWork)")
        val method = AopTargetServiceImpl::class.java.getMethod("doWork")
        assertTrue(pc.matches(AopTargetServiceImpl::class.java, method))
    }

    @Test
    fun `pointcut should match fully qualified class name`() {
        val pc = PointcutExpression.parse("execution(top.wcpe.taboolib.ioc.aop.AopTargetServiceImpl.doWork)")
        val method = AopTargetServiceImpl::class.java.getMethod("doWork")
        assertTrue(pc.matches(AopTargetServiceImpl::class.java, method))
    }

    @Test
    fun `pointcut should throw on invalid expression without dot`() {
        assertThrows(IllegalArgumentException::class.java) {
            PointcutExpression.parse("execution(noDotsHere)")
        }
    }

    @Test
    fun `pointcut should parse bare expression without execution wrapper`() {
        val pc = PointcutExpression.parse("AopTargetServiceImpl.doWork")
        val method = AopTargetServiceImpl::class.java.getMethod("doWork")
        assertTrue(pc.matches(AopTargetServiceImpl::class.java, method))
    }

    // ── @Pointcut 引用解析测试 ──

    @Test
    fun `pointcut reference should resolve and fire before advice`() {
        PointcutRefAspect.callLog.clear()

        val ctx = IocTestContext()
        ctx.register(PointcutRefAspect::class.java)
        ctx.register(AopTargetServiceImpl::class.java)
        ctx.initialize()

        val service = ctx.getBean(AopTargetService::class.java)
        assertNotNull(service)
        service!!.doWork()

        assertTrue(PointcutRefAspect.callLog.contains("pointcutRef:before"))
    }

    // ── 多 @Around 链测试 ──

    @Test
    fun `multiple around aspects should all execute`() {
        FirstAroundAspect.callLog.clear()
        SecondAroundAspect.callLog.clear()

        val ctx = IocTestContext()
        ctx.register(FirstAroundAspect::class.java)
        ctx.register(SecondAroundAspect::class.java)
        ctx.register(AopTargetServiceImpl::class.java)
        ctx.initialize()

        val service = ctx.getBean(AopTargetService::class.java)
        assertNotNull(service)
        service!!.doWork()

        assertTrue(FirstAroundAspect.callLog.isNotEmpty(), "First around should have been called")
        assertTrue(SecondAroundAspect.callLog.isNotEmpty(), "Second around should have been called")
    }

    // ── @Around 短路测试 ──

    @Test
    fun `around short circuit should skip target method`() {
        ShortCircuitServiceImpl.called = false

        val ctx = IocTestContext()
        ctx.register(ShortCircuitAspect::class.java)
        ctx.register(ShortCircuitServiceImpl::class.java)
        ctx.initialize()

        val service = ctx.getBean(ShortCircuitService::class.java)
        assertNotNull(service)
        val result = service!!.compute()

        assertEquals("intercepted", result)
        assertFalse(ShortCircuitServiceImpl.called, "Target method should not have been called")
    }

    // ── @Around 修改返回值测试 ──

    @Test
    fun `around should be able to modify return value`() {
        val ctx = IocTestContext()
        ctx.register(ModifyReturnAspect::class.java)
        ctx.register(ModifyReturnServiceImpl::class.java)
        ctx.initialize()

        val service = ctx.getBean(ModifyReturnService::class.java)
        assertNotNull(service)
        val result = service!!.getValue()

        assertEquals("original:modified", result)
    }

    // ── 异常传播测试 ──

    @Test
    fun `after advice should execute even when target throws`() {
        ExceptionAfterAspect.afterCalled = false

        val ctx = IocTestContext()
        ctx.register(ExceptionAfterAspect::class.java)
        ctx.register(ExceptionTargetServiceImpl::class.java)
        ctx.initialize()

        val service = ctx.getBean(ExceptionTargetService::class.java)
        assertNotNull(service)

        val ex = assertThrows(RuntimeException::class.java) {
            service!!.boom()
        }
        assertTrue(ex.message?.contains("boom!") == true || ex.cause?.message?.contains("boom!") == true)
        assertTrue(ExceptionAfterAspect.afterCalled, "@After should have been called despite exception")
    }

    // ── 带参数方法测试 ──

    @Test
    fun `proxy should correctly pass method parameters`() {
        ParameterizedBeforeAspect.beforeCalled = false

        val ctx = IocTestContext()
        ctx.register(ParameterizedBeforeAspect::class.java)
        ctx.register(ParameterizedServiceImpl::class.java)
        ctx.initialize()

        val service = ctx.getBean(ParameterizedService::class.java)
        assertNotNull(service)
        val result = service!!.greet("World")

        assertEquals("hello World", result)
        assertTrue(ParameterizedBeforeAspect.beforeCalled)
    }

    // ── AOP + DI 集成测试 ──

    @Test
    fun `injected proxy should trigger advice`() {
        TestLoggingAspect.callLog.clear()

        val ctx = IocTestContext()
        ctx.register(TestLoggingAspect::class.java)
        ctx.register(AopTargetServiceImpl::class.java)
        ctx.register(AopConsumerService::class.java)
        ctx.initialize()

        val consumer = ctx.getBean(AopConsumerService::class.java)
        assertNotNull(consumer)
        assertNotNull(consumer!!.aopTargetService)

        consumer.aopTargetService.doWork()
        assertTrue(TestLoggingAspect.callLog.contains("before:doWork"))
    }

    // ── 多接口代理测试 ──

    @Test
    fun `proxy should implement all interfaces of target bean`() {
        MultiInterfaceAspect.callLog.clear()

        val ctx = IocTestContext()
        ctx.register(MultiInterfaceAspect::class.java)
        ctx.register(MultiInterfaceBean::class.java)
        ctx.initialize()

        val greeter = ctx.getBean(Greeter::class.java)
        assertNotNull(greeter)
        assertEquals("hi", greeter!!.hi())

        val farewell = ctx.getBean(Farewell::class.java)
        assertNotNull(farewell)
        assertEquals("bye", farewell!!.bye())

        assertTrue(MultiInterfaceAspect.callLog.size >= 2)
    }
}

// ── 测试用组件 ──

interface AopTargetService {
    fun doWork(): String
}

@Component
class AopTargetServiceImpl : AopTargetService {
    override fun doWork(): String = "done"
}

@Component
class NoInterfaceBean {
    fun hello(): String = "hello"
}

@Aspect
class TestLoggingAspect {

    companion object {
        val callLog = mutableListOf<String>()
    }

    @Before("execution(AopTargetServiceImpl.doWork)")
    fun beforeDoWork() {
        callLog.add("before:doWork")
    }

    @After("execution(AopTargetServiceImpl.doWork)")
    fun afterDoWork() {
        callLog.add("after:doWork")
    }

    @Around("execution(AopTargetServiceImpl.doWork)")
    fun aroundDoWork(invocation: MethodInvocation): Any? {
        callLog.add("around:before")
        val result = invocation.proceed()
        callLog.add("around:after")
        return result
    }
}

// ── 1. PointcutExpression 边界用例的测试夹具 ──

// (无需额外夹具，直接使用已有的 AopTargetServiceImpl)

// ── 2. @Pointcut 引用解析的测试夹具 ──

@Aspect
class PointcutRefAspect {

    companion object {
        val callLog = mutableListOf<String>()
    }

    @Pointcut("execution(AopTargetServiceImpl.doWork)")
    fun targetPointcut() {}

    @Before("targetPointcut")
    fun beforeViaRef() {
        callLog.add("pointcutRef:before")
    }
}

// ── 3. 多重 @Around 链的测试夹具 ──

@Aspect
class FirstAroundAspect {

    companion object {
        val callLog = mutableListOf<String>()
    }

    @Around("execution(AopTargetServiceImpl.doWork)")
    fun firstAround(invocation: MethodInvocation): Any? {
        callLog.add("first:around:before")
        val result = invocation.proceed()
        callLog.add("first:around:after")
        return result
    }
}

@Aspect
class SecondAroundAspect {

    companion object {
        val callLog = mutableListOf<String>()
    }

    @Around("execution(AopTargetServiceImpl.doWork)")
    fun secondAround(invocation: MethodInvocation): Any? {
        callLog.add("second:around:before")
        val result = invocation.proceed()
        callLog.add("second:around:after")
        return result
    }
}

// ── 4. @Around 短路（不调用 proceed）的测试夹具 ──

interface ShortCircuitService {
    fun compute(): String
}

@Component
class ShortCircuitServiceImpl : ShortCircuitService {

    companion object {
        var called = false
    }

    override fun compute(): String {
        called = true
        return "original"
    }
}

@Aspect
class ShortCircuitAspect {

    @Around("execution(ShortCircuitServiceImpl.compute)")
    fun intercept(invocation: MethodInvocation): Any? {
        // 不调用 proceed()，直接返回
        return "intercepted"
    }
}

// ── 5. @Around 修改返回值的测试夹具 ──

interface ModifyReturnService {
    fun getValue(): String
}

@Component
class ModifyReturnServiceImpl : ModifyReturnService {
    override fun getValue(): String = "original"
}

@Aspect
class ModifyReturnAspect {

    @Around("execution(ModifyReturnServiceImpl.getValue)")
    fun modify(invocation: MethodInvocation): Any? {
        val result = invocation.proceed()
        return "$result:modified"
    }
}

// ── 6. 异常传播的测试夹具 ──

interface ExceptionTargetService {
    fun boom(): String
}

@Component
class ExceptionTargetServiceImpl : ExceptionTargetService {
    override fun boom(): String {
        throw RuntimeException("boom!")
    }
}

@Aspect
class ExceptionAfterAspect {

    companion object {
        var afterCalled = false
    }

    @After("execution(ExceptionTargetServiceImpl.boom)")
    fun afterBoom() {
        afterCalled = true
    }
}

// ── 7. 带参数方法的测试夹具 ──

interface ParameterizedService {
    fun greet(name: String): String
}

@Component
class ParameterizedServiceImpl : ParameterizedService {
    override fun greet(name: String): String = "hello $name"
}

@Aspect
class ParameterizedBeforeAspect {

    companion object {
        var beforeCalled = false
    }

    @Before("execution(ParameterizedServiceImpl.greet)")
    fun beforeGreet() {
        beforeCalled = true
    }
}

// ── 8. AOP + DI 集成的测试夹具 ──

@Component
class AopConsumerService {
    @Inject
    lateinit var aopTargetService: AopTargetService
}

// ── 9. 多接口的测试夹具 ──

interface Greeter {
    fun hi(): String
}

interface Farewell {
    fun bye(): String
}

@Component
class MultiInterfaceBean : Greeter, Farewell {
    override fun hi(): String = "hi"
    override fun bye(): String = "bye"
}

@Aspect
class MultiInterfaceAspect {

    companion object {
        val callLog = mutableListOf<String>()
    }

    @Before("execution(MultiInterfaceBean.*)")
    fun beforeAny() {
        callLog.add("multi:before")
    }
}

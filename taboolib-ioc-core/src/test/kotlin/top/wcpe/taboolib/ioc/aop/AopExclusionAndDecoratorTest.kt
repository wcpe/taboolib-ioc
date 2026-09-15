package top.wcpe.taboolib.ioc.aop

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import top.wcpe.taboolib.ioc.IocTestContext
import top.wcpe.taboolib.ioc.annotation.Around
import top.wcpe.taboolib.ioc.annotation.Aspect
import top.wcpe.taboolib.ioc.annotation.Component
import top.wcpe.taboolib.ioc.annotation.NoAspect
import top.wcpe.taboolib.ioc.annotation.WrapWith
import top.wcpe.taboolib.ioc.bean.MethodInvocation

/**
 * ⑤ 声明式排除 AOP（[NoAspect]）与 ④ 手写装饰器（[WrapWith]）的行为契约。
 */
class AopExclusionAndDecoratorTest {

    @Test
    fun `类级 NoAspect 的 Bean 完全不被代理`() {
        ExclusionAspect.log.clear()

        val ctx = IocTestContext()
        ctx.register(ExclusionAspect::class.java)
        ctx.register(FullyExcludedServiceImpl::class.java)
        ctx.initialize()

        val bean = ctx.getBean(FullyExcludedService::class.java)
        assertNotNull(bean)
        assertFalse(
            java.lang.reflect.Proxy.isProxyClass(bean!!.javaClass),
            "整类标注 @NoAspect 时不应创建代理（零额外开销）"
        )
        assertEquals("excluded", bean.work())
        assertTrue(ExclusionAspect.log.isEmpty(), "被排除的类不应触发任何通知")
    }

    @Test
    fun `方法级 NoAspect 只排除该方法`() {
        ExclusionAspect.log.clear()

        val ctx = IocTestContext()
        ctx.register(ExclusionAspect::class.java)
        ctx.register(PartiallyExcludedServiceImpl::class.java)
        ctx.initialize()

        val bean = ctx.getBean(PartiallyExcludedService::class.java)
        assertNotNull(bean)
        assertTrue(
            java.lang.reflect.Proxy.isProxyClass(bean!!.javaClass),
            "存在未被排除的方法时仍应创建代理"
        )

        bean.hot()
        assertTrue(ExclusionAspect.log.isEmpty(), "标注 @NoAspect 的高频方法不应触发通知")

        bean.rare()
        assertEquals(listOf("rare"), ExclusionAspect.log, "未被排除的方法应照常触发通知")
    }

    @Test
    fun `WrapWith 用装饰器替换实例`() {
        val ctx = IocTestContext()
        ctx.register(WrappedServiceImpl::class.java)
        ctx.initialize()

        val bean = ctx.getBean(WrappedService::class.java)
        assertNotNull(bean)
        assertTrue(bean is WrappedServiceDecorator, "实例应被 @WrapWith 装饰器替换，实际=${bean!!.javaClass.name}")
        assertEquals("decorated:impl:hi", bean.greet("hi"))
    }

    @Test
    fun `WrapWith 装饰器保持原 Bean 的注入与生命周期`() {
        WrappedServiceImpl.postConstructCalled = false

        val ctx = IocTestContext()
        ctx.register(WrappedServiceImpl::class.java)
        ctx.initialize()

        val bean = ctx.getBean(WrappedService::class.java)
        assertTrue(bean is WrappedServiceDecorator)
        assertTrue(
            WrappedServiceImpl.postConstructCalled,
            "装饰器替换发生在 @PostConstruct 之后，原 Bean 的生命周期回调必须照常执行"
        )
    }
}

// ── ⑤ 夹具：类级 / 方法级 @NoAspect ──

interface FullyExcludedService {
    fun work(): String
}

@Component
@NoAspect
class FullyExcludedServiceImpl : FullyExcludedService {
    override fun work(): String = "excluded"
}

interface PartiallyExcludedService {
    fun hot(): String

    fun rare(): String
}

@Component
class PartiallyExcludedServiceImpl : PartiallyExcludedService {

    @NoAspect
    override fun hot(): String = "hot"

    override fun rare(): String = "rare"
}

@Aspect
class ExclusionAspect {

    companion object {
        val log = mutableListOf<String>()
    }

    @Around("execution(FullyExcludedServiceImpl.work)")
    fun aroundExcludedClass(invocation: MethodInvocation): Any? {
        log.add("work")
        return invocation.proceed()
    }

    @Around("execution(PartiallyExcludedServiceImpl.*)")
    fun aroundPartiallyExcluded(invocation: MethodInvocation): Any? {
        log.add(invocation.method.name)
        return invocation.proceed()
    }
}

// ── ④ 夹具：手写装饰器 ──

interface WrappedService {
    fun greet(name: String): String
}

@Component
@WrapWith(WrappedServiceDecorator::class)
class WrappedServiceImpl : WrappedService {

    companion object {
        @Volatile
        var postConstructCalled: Boolean = false
    }

    @top.wcpe.taboolib.ioc.annotation.PostConstruct
    fun onInit() {
        postConstructCalled = true
    }

    override fun greet(name: String): String = "impl:$name"
}

class WrappedServiceDecorator(private val delegate: WrappedService) : WrappedService {
    override fun greet(name: String): String = "decorated:" + delegate.greet(name)
}

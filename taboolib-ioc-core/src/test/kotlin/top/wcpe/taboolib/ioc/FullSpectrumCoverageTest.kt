package top.wcpe.taboolib.ioc

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import top.wcpe.taboolib.ioc.annotation.Around
import top.wcpe.taboolib.ioc.annotation.Aspect
import top.wcpe.taboolib.ioc.annotation.Component
import top.wcpe.taboolib.ioc.annotation.Controller
import top.wcpe.taboolib.ioc.annotation.Inject
import top.wcpe.taboolib.ioc.annotation.Order
import top.wcpe.taboolib.ioc.annotation.PostConstruct
import top.wcpe.taboolib.ioc.annotation.PreDestroy
import top.wcpe.taboolib.ioc.annotation.Repository
import top.wcpe.taboolib.ioc.annotation.Service
import top.wcpe.taboolib.ioc.annotation.WrapWith
import top.wcpe.taboolib.ioc.scope.ThreadBeanScope
import top.wcpe.taboolib.ioc.aop.WovenTarget
import top.wcpe.taboolib.ioc.bean.BeanContainer
import top.wcpe.taboolib.ioc.bean.BeanDestroyedEvent
import top.wcpe.taboolib.ioc.bean.BeanNotOfRequiredTypeException
import top.wcpe.taboolib.ioc.bean.BeanScopes
import top.wcpe.taboolib.ioc.bean.ContainerShutdownEvent
import top.wcpe.taboolib.ioc.bean.MethodInvocation

/**
 * **全种类补齐**：把此前"有实现但没测试"的类别补上，并覆盖新特性（①⑤④②）之间的组合场景。
 *
 * 覆盖清单（对照 README「当前支持」逐项核对后的缺口）：
 * 1. `@Repository` / `@Controller` 与 `@Service` 等价（此前**零测试**）
 * 2. 容器关闭链路事件：`BeanDestroyedEvent` + `ContainerShutdownEvent`（此前只测了创建/初始化）
 * 3. AOP 通知顺序：`@Order` 对多个 `@Around` 嵌套顺序的影响（此前无断言）
 * 4. 容器守卫语义：未初始化时的 API 行为（此前无断言）
 * 5. 类型契约：被 AOP 代理后按实现类查询的错误类型（此前无断言）
 * 6. `getThreadScope()`：线程作用域访问器（此前零测试）
 * 7. 组合：`WovenTarget` + `@WrapWith`（②与④叠加）
 */
class FullSpectrumCoverageTest {

    @AfterEach
    fun teardown() {
        BeanContainer.resetForTesting()
        OrderAspectLog.clear()
    }

    @Test
    fun `Repository 与 Controller 与 Service 等价，都可被注册与注入`() {
        val ctx = IocTestContext()
        ctx.register(RepositoryBeanFixture::class.java)
        ctx.register(ControllerBeanFixture::class.java)
        ctx.register(ServiceBeanFixture::class.java)
        ctx.register(PrototypeStereotypeConsumer::class.java)
        ctx.initialize()

        assertNotNull(ctx.getBean(RepositoryBeanFixture::class.java), "@Repository 应被识别为组件")
        assertNotNull(ctx.getBean(ControllerBeanFixture::class.java), "@Controller 应被识别为组件")
        assertNotNull(ctx.getBean(ServiceBeanFixture::class.java), "@Service 应被识别为组件")

        val consumer = ctx.getBean(PrototypeStereotypeConsumer::class.java)
        assertNotNull(consumer)
        assertEquals("repo", consumer!!.repository.tag())
        assertEquals("ctrl", consumer.controller.tag())
        assertEquals("svc", consumer.service.tag())
        assertTrue(
            ctx.getBeanNames().containsAll(listOf("repositoryBeanFixture", "controllerBeanFixture")),
            "@Repository/@Controller 的默认 Bean 名应进入 getBeanNames：${ctx.getBeanNames()}"
        )
    }

    @Test
    fun `容器关闭链路事件：BeanDestroyedEvent 与 ContainerShutdownEvent 都会触发`() {
        val destroyed = mutableListOf<String>()
        var shutdownCount = 0

        val ctx = IocTestContext()
        ctx.register(DestroyableBeanFixture::class.java)
        ctx.lifecycleManager.eventBus.on<BeanDestroyedEvent> { destroyed.add(it.beanName) }
        ctx.lifecycleManager.eventBus.on<ContainerShutdownEvent> { shutdownCount++ }
        ctx.initialize()

        val bean = ctx.getBean(DestroyableBeanFixture::class.java)
        assertNotNull(bean)
        assertTrue(DestroyableBeanFixture.created, "@PostConstruct 应已执行")

        ctx.shutdown()

        assertTrue(destroyed.contains("destroyableBeanFixture"), "销毁事件应带 Bean 名：$destroyed")
        assertTrue(DestroyableBeanFixture.destroyed, "@PreDestroy 应已执行")
        assertEquals(1, shutdownCount, "容器关闭事件应恰好触发一次")
    }

    @Test
    fun `AOP 通知顺序：Order 值小的 @Around 在外层`() {
        OrderAspectLog.clear()

        val ctx = IocTestContext()
        ctx.register(OuterOrderAspect::class.java)
        ctx.register(InnerOrderAspect::class.java)
        ctx.register(OrderedTargetServiceImpl::class.java)
        ctx.initialize()

        val service = ctx.getBean(OrderedTargetService::class.java)
        assertNotNull(service)
        assertEquals("ok", service!!.run())

        assertEquals(
            listOf("outer:before", "inner:before", "inner:after", "outer:after"),
            OrderAspectLog.events,
            "@Order(1) 应包裹 @Order(2)：值小的在最外层"
        )
    }

    @Test
    fun `容器守卫：未初始化时 API 不抛异常且给出空结果`() {
        BeanContainer.resetForTesting()

        assertEquals(null, BeanContainer.getBean(String::class.java), "未初始化时 getBean 应返回 null")
        assertEquals(emptyList<Any>(), BeanContainer.getBeansOfType(String::class.java), "未初始化时枚举应返回空列表")
        assertEquals(false, BeanContainer.containsBean("whatever"))
        assertEquals(emptySet<String>(), BeanContainer.getBeanNames())
        assertEquals(null, BeanContainer.getThreadScope(), "未注册线程作用域时应返回 null")
    }

    @Test
    fun `类型契约：被 AOP 代理后，按实现类查询抛出 BeanNotOfRequiredTypeException`() {
        val ctx = IocTestContext()
        ctx.register(TypeContractAspect::class.java)
        ctx.register(TypeContractServiceImpl::class.java)
        ctx.initialize()

        // 按接口查询正常
        assertNotNull(ctx.getBean(TypeContractService::class.java))

        // 按实现类查询：缓存的是 JDK 代理，不是实现类实例 → 必须给出带修复指引的类型化异常，
        // 而不是让 ClassCastException 推迟到业务调用点
        val ex = assertThrows(BeanNotOfRequiredTypeException::class.java) {
            ctx.getBean(TypeContractServiceImpl::class.java)
        }
        assertTrue(ex.message?.contains(TypeContractServiceImpl::class.java.name) == true, "异常信息应含目标类型：${ex.message}")
    }

    @Test
    fun `getThreadScope：注册线程作用域后可通过容器访问`() {
        BeanContainer.resetForTesting()
        assertEquals(null, BeanContainer.getThreadScope())

        val scope = ThreadBeanScope()
        BeanContainer.registerScope(BeanScopes.THREAD, scope)

        assertSame(scope, BeanContainer.getThreadScope(), "应返回注册进去的同一个作用域实例")
    }

    @Test
    fun `组合：WovenTarget 的类被 @WrapWith 装饰且不被代理`() {
        val ctx = IocTestContext()
        ctx.register(WovenDecoratedServiceImpl::class.java)
        ctx.initialize()

        val bean = ctx.getBean(WovenDecoratedService::class.java)
        assertNotNull(bean)
        assertTrue(bean is WovenDecoratedServiceDecorator, "应被装饰器替换，实际=${bean!!.javaClass.name}")
        assertFalse(java.lang.reflect.Proxy.isProxyClass(bean.javaClass), "WovenTarget 不应再创建代理")
        assertEquals("decorated:<impl:hi>", bean.greet("hi"))
    }

    @Test
    fun `线程作用域语义：同线程同实例、跨线程不同实例`() {
        val ctx = IocTestContext()
        ctx.registerScope(BeanScopes.THREAD, ThreadBeanScope())
        ctx.register(ThreadScopedBeanFixture::class.java)
        ctx.initialize()

        val here = ctx.getBean(ThreadScopedBeanFixture::class.java)
        val again = ctx.getBean(ThreadScopedBeanFixture::class.java)
        assertNotNull(here)
        assertSame(here, again, "同线程应复用同一实例")

        val other = java.util.concurrent.CompletableFuture.supplyAsync {
            ctx.getBean(ThreadScopedBeanFixture::class.java)
        }.get()
        assertNotNull(other)
        assertNotSame(here, other, "不同线程应拿到各自实例")
    }
}

// ── 1. 三种原型注解 ──

@Repository
class RepositoryBeanFixture {
    fun tag(): String = "repo"
}

@Controller
class ControllerBeanFixture {
    fun tag(): String = "ctrl"
}

@Service
class ServiceBeanFixture {
    fun tag(): String = "svc"
}

@Component
class PrototypeStereotypeConsumer {

    @Inject
    lateinit var repository: RepositoryBeanFixture

    @Inject
    lateinit var controller: ControllerBeanFixture

    @Inject
    lateinit var service: ServiceBeanFixture
}

// ── 2. 销毁链路 ──

@Component
class DestroyableBeanFixture {

    companion object {
        @Volatile
        var created = false

        @Volatile
        var destroyed = false
    }

    @PostConstruct
    fun onInit() {
        created = true
    }

    @PreDestroy
    fun onDestroy() {
        destroyed = true
    }
}

// ── 3. @Order 通知顺序 ──

interface OrderedTargetService {
    fun run(): String
}

@Component
class OrderedTargetServiceImpl : OrderedTargetService {
    override fun run(): String = "ok"
}

object OrderAspectLog {
    val events = mutableListOf<String>()

    fun clear() = events.clear()
}

@Aspect
@Order(1)
class OuterOrderAspect {

    @Around("execution(OrderedTargetServiceImpl.run)")
    fun around(invocation: MethodInvocation): Any? {
        OrderAspectLog.events.add("outer:before")
        val result = invocation.proceed()
        OrderAspectLog.events.add("outer:after")
        return result
    }
}

@Aspect
@Order(2)
class InnerOrderAspect {

    @Around("execution(OrderedTargetServiceImpl.run)")
    fun around(invocation: MethodInvocation): Any? {
        OrderAspectLog.events.add("inner:before")
        val result = invocation.proceed()
        OrderAspectLog.events.add("inner:after")
        return result
    }
}

// ── 5. 类型契约 ──

interface TypeContractService {
    fun ping(): String
}

@Component
class TypeContractServiceImpl : TypeContractService {
    override fun ping(): String = "pong"
}

@Aspect
class TypeContractAspect {

    @Around("execution(TypeContractServiceImpl.ping)")
    fun around(invocation: MethodInvocation): Any? = invocation.proceed()
}

// ── 7. WovenTarget + @WrapWith 组合 ──

interface WovenDecoratedService {
    fun greet(name: String): String
}

@Component
@WrapWith(WovenDecoratedServiceDecorator::class)
class WovenDecoratedServiceImpl : WovenDecoratedService, WovenTarget {
    override fun greet(name: String): String = "<impl:$name>"
}

class WovenDecoratedServiceDecorator(private val delegate: WovenDecoratedService) : WovenDecoratedService {
    override fun greet(name: String): String = "decorated:${delegate.greet(name)}"
}

// ── 8. 线程作用域 ──

@Component
@top.wcpe.taboolib.ioc.annotation.ThreadScope
class ThreadScopedBeanFixture {
    val id: Int = NEXT.incrementAndGet()

    companion object {
        private val NEXT = java.util.concurrent.atomic.AtomicInteger(0)
    }
}

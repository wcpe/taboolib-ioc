package top.wcpe.taboolib.ioc

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import top.wcpe.taboolib.ioc.annotation.*
import top.wcpe.taboolib.ioc.bean.BeanCreatedEvent
import top.wcpe.taboolib.ioc.bean.BeanDestroyedEvent
import top.wcpe.taboolib.ioc.bean.ContainerInitializedEvent
import top.wcpe.taboolib.ioc.bean.ContainerShutdownEvent
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors

/**
 * 第二批补充测试：@Value、事件机制、prototype PreDestroy、并发 singleton、@Lazy 字段代理
 */
class Batch2SupplementTest {

    // ==================== @Value 属性注入 ====================

    @Test
    fun `Value - 从系统属性注入 String`() {
        System.setProperty("test.app.name", "hello-ioc")
        try {
            val ctx = IocTestContext()
            ctx.register(ValueStringBean::class.java)
            ctx.initialize()
            val bean = ctx.getBean(ValueStringBean::class.java)
            assertEquals("hello-ioc", bean!!.appName)
        } finally {
            System.clearProperty("test.app.name")
        }
    }

    @Test
    fun `Value - 带默认值且属性不存在时使用默认值`() {
        System.clearProperty("test.missing.prop")
        val ctx = IocTestContext()
        ctx.register(ValueDefaultBean::class.java)
        ctx.initialize()
        val bean = ctx.getBean(ValueDefaultBean::class.java)
        assertEquals("fallback", bean!!.value)
    }

    @Test
    fun `Value - 注入 Int 类型`() {
        System.setProperty("test.port", "8080")
        try {
            val ctx = IocTestContext()
            ctx.register(ValueIntBean::class.java)
            ctx.initialize()
            val bean = ctx.getBean(ValueIntBean::class.java)
            assertEquals(8080, bean!!.port)
        } finally {
            System.clearProperty("test.port")
        }
    }

    @Test
    fun `Value - 注入 Boolean 类型`() {
        System.setProperty("test.debug", "true")
        try {
            val ctx = IocTestContext()
            ctx.register(ValueBoolBean::class.java)
            ctx.initialize()
            val bean = ctx.getBean(ValueBoolBean::class.java)
            assertTrue(bean!!.debug)
        } finally {
            System.clearProperty("test.debug")
        }
    }

    @Test
    fun `Value - 纯文本字面量`() {
        val ctx = IocTestContext()
        ctx.register(ValueLiteralBean::class.java)
        ctx.initialize()
        val bean = ctx.getBean(ValueLiteralBean::class.java)
        assertEquals("literal-text", bean!!.text)
    }

    // ==================== Bean 事件机制 ====================

    @Test
    fun `EventBus - BeanCreatedEvent 在 Bean 创建后触发`() {
        val events = CopyOnWriteArrayList<String>()
        val ctx = IocTestContext()
        ctx.lifecycleManager.eventBus.on<BeanCreatedEvent> { events.add(it.beanName) }
        ctx.register(SimpleEventBean::class.java)
        ctx.initialize()
        assertTrue(events.contains("simpleEventBean"))
    }

    @Test
    fun `EventBus - ContainerInitializedEvent 在初始化完成后触发`() {
        var count = -1
        val ctx = IocTestContext()
        ctx.lifecycleManager.eventBus.on<ContainerInitializedEvent> { count = it.beanCount }
        ctx.register(SimpleEventBean::class.java)
        ctx.initialize()
        assertEquals(1, count)
    }

    @Test
    fun `EventBus - BeanDestroyedEvent 和 ContainerShutdownEvent 在关闭时触发`() {
        val destroyed = CopyOnWriteArrayList<String>()
        var shutdownFired = false
        val ctx = IocTestContext()
        ctx.lifecycleManager.eventBus.on<BeanDestroyedEvent> { destroyed.add(it.beanName) }
        ctx.lifecycleManager.eventBus.on<ContainerShutdownEvent> { shutdownFired = true }
        ctx.register(SimpleEventBean::class.java)
        ctx.initialize()
        ctx.lifecycleManager.shutdown()
        assertTrue(destroyed.contains("simpleEventBean"))
        assertTrue(shutdownFired)
    }

    // ==================== prototype @PreDestroy 不在容器关闭时调用 ====================

    @Test
    fun `Prototype - PreDestroy 不在容器关闭时调用`() {
        PrototypeDestroyTracker.destroyed = false
        val ctx = IocTestContext()
        ctx.register(PrototypeWithDestroy::class.java)
        ctx.initialize()
        ctx.getBean(PrototypeWithDestroy::class.java) // 创建一个实例
        ctx.lifecycleManager.shutdown()
        assertFalse(PrototypeDestroyTracker.destroyed, "prototype Bean 的 @PreDestroy 不应在容器关闭时调用")
    }

    // ==================== 并发 singleton 创建 ====================

    @Test
    fun `并发获取 Lazy singleton 只创建一个实例`() {
        ConcurrentSingletonBean.count = 0
        val ctx = IocTestContext()
        ctx.register(ConcurrentSingletonBean::class.java)
        ctx.initialize()

        val threads = 10
        val latch = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(threads)
        val results = CopyOnWriteArrayList<Any>()

        repeat(threads) {
            executor.submit {
                latch.await()
                val bean = ctx.getBean(ConcurrentSingletonBean::class.java)
                if (bean != null) results.add(bean)
            }
        }
        latch.countDown()
        executor.shutdown()
        executor.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)

        assertEquals(threads, results.size)
        val distinct = results.distinctBy { System.identityHashCode(it) }
        assertEquals(1, distinct.size, "所有线程应获取同一个实例")
        assertEquals(1, ConcurrentSingletonBean.count, "构造函数应只调用一次")
    }

    // ==================== @Lazy 字段代理 ====================

    @Test
    fun `Lazy 字段代理 - 接口类型字段延迟解析`() {
        val ctx = IocTestContext()
        ctx.register(LazyFieldTargetImpl::class.java)
        ctx.register(LazyFieldConsumer::class.java)
        ctx.initialize()

        val consumer = ctx.getBean(LazyFieldConsumer::class.java)
        assertNotNull(consumer)
        // 代理已注入但尚未解析真实实例
        assertEquals("lazy-target", consumer!!.getTargetValue())
    }

    // ==================== 测试用 Bean ====================

    @Service
    class ValueStringBean {
        @Value("\${test.app.name}")
        lateinit var appName: String
    }

    @Service
    class ValueDefaultBean {
        @Value("\${test.missing.prop:fallback}")
        lateinit var value: String
    }

    @Service
    class ValueIntBean {
        @Value("\${test.port}")
        var port: Int = 0
    }

    @Service
    class ValueBoolBean {
        @Value("\${test.debug}")
        var debug: Boolean = false
    }

    @Service
    class ValueLiteralBean {
        @Value("literal-text")
        lateinit var text: String
    }

    @Service
    class SimpleEventBean

    object PrototypeDestroyTracker {
        var destroyed = false
    }

    @Component
    @Prototype
    class PrototypeWithDestroy {
        @PreDestroy
        fun onDestroy() {
            PrototypeDestroyTracker.destroyed = true
        }
    }

    @Component
    @Lazy
    class ConcurrentSingletonBean {
        companion object {
            @Volatile
            var count = 0
        }
        init {
            synchronized(ConcurrentSingletonBean::class.java) { count++ }
        }
    }

    interface LazyFieldTarget {
        fun value(): String
    }

    @Component("lazyFieldTargetImpl")
    class LazyFieldTargetImpl : LazyFieldTarget {
        override fun value() = "lazy-target"
    }

    @Service
    class LazyFieldConsumer {
        @Inject
        @Lazy
        lateinit var target: LazyFieldTarget

        fun getTargetValue(): String = target.value()
    }
}

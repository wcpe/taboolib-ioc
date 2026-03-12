package top.wcpe.taboolib.ioc.lifecycle

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import top.wcpe.taboolib.ioc.IocTestContext
import top.wcpe.taboolib.ioc.annotation.*

/**
 * @PostEnable 生命周期钩子测试
 */
class PostEnableTest {

    @Test
    fun `PostEnable 在 initialize 后手动调用时执行`() {
        PostEnableTracker.executed = false

        val ctx = IocTestContext()
        ctx.register(PostEnableBean::class.java)
        ctx.initialize()

        assertFalse(PostEnableTracker.executed, "@PostEnable 不应在 initialize 时执行")

        ctx.invokePostEnable()

        assertTrue(PostEnableTracker.executed, "@PostEnable 应在 invokePostEnable 后执行")
    }

    @Test
    fun `PostEnable 执行时所有 Bean 已创建完毕`() {
        PostEnableDepTracker.depAvailable = false

        val ctx = IocTestContext()
        ctx.register(DepBeanForPostEnable::class.java)
        ctx.register(PostEnableDepCheckBean::class.java)
        ctx.initialize()
        ctx.invokePostEnable()

        assertTrue(PostEnableDepTracker.depAvailable, "@PostEnable 执行时依赖应已注入")
    }

    @Test
    fun `PostConstruct 在 PostEnable 之前执行`() {
        OrderTracker.order.clear()

        val ctx = IocTestContext()
        ctx.register(OrderedLifecycleBean::class.java)
        ctx.initialize()
        ctx.invokePostEnable()

        assertEquals(listOf("PostConstruct", "PostEnable"), OrderTracker.order)
    }

    @Test
    fun `多个 Bean 的 PostEnable 按初始化顺序执行`() {
        MultiPostEnableTracker.order.clear()

        val ctx = IocTestContext()
        ctx.register(PostEnableFirst::class.java)
        ctx.register(PostEnableSecond::class.java)
        ctx.initialize()
        ctx.invokePostEnable()

        assertEquals(2, MultiPostEnableTracker.order.size)
        assertEquals("First", MultiPostEnableTracker.order[0])
        assertEquals("Second", MultiPostEnableTracker.order[1])
    }

    @Test
    fun `没有 PostEnable 的 Bean 不受影响`() {
        val ctx = IocTestContext()
        ctx.register(NormalBean::class.java)
        ctx.initialize()
        ctx.invokePostEnable()

        val bean = ctx.getBean(NormalBean::class.java)
        assertNotNull(bean)
    }

    // ==================== 测试用 Bean ====================

    object PostEnableTracker {
        var executed = false
    }

    @Service
    class PostEnableBean {
        @PostEnable
        fun onEnable() {
            PostEnableTracker.executed = true
        }
    }

    @Service
    class DepBeanForPostEnable

    object PostEnableDepTracker {
        var depAvailable = false
    }

    @Service
    class PostEnableDepCheckBean {
        @Inject
        lateinit var dep: DepBeanForPostEnable

        @PostEnable
        fun onEnable() {
            PostEnableDepTracker.depAvailable = this::dep.isInitialized
        }
    }

    object OrderTracker {
        val order = mutableListOf<String>()
    }

    @Service
    class OrderedLifecycleBean {
        @PostConstruct
        fun onInit() {
            OrderTracker.order.add("PostConstruct")
        }

        @PostEnable
        fun onEnable() {
            OrderTracker.order.add("PostEnable")
        }
    }

    object MultiPostEnableTracker {
        val order = mutableListOf<String>()
    }

    @Service
    class PostEnableFirst {
        @PostEnable
        fun onEnable() {
            MultiPostEnableTracker.order.add("First")
        }
    }

    @Service
    class PostEnableSecond @Inject constructor(
        private val first: PostEnableFirst
    ) {
        @PostEnable
        fun onEnable() {
            MultiPostEnableTracker.order.add("Second")
        }
    }

    @Service
    class NormalBean
}

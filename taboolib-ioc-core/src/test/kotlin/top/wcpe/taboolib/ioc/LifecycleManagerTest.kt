package top.wcpe.taboolib.ioc

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import top.wcpe.taboolib.ioc.annotation.*

/**
 * LifecycleManager 测试
 */
class LifecycleManagerTest {

    @Test
    fun `initialize 创建 eager singleton`() {
        val ctx = IocTestContext()
        ctx.register(EagerBean::class.java)
        ctx.initialize()

        val bean = ctx.getBean(EagerBean::class.java)
        assertNotNull(bean)
    }

    @Test
    fun `shutdown 按逆序调用 PreDestroy`() {
        DestroyOrderTracker.order.clear()

        val ctx = IocTestContext()
        ctx.register(DestroyFirst::class.java)
        ctx.register(DestroySecond::class.java)
        ctx.initialize()

        // 确保两个 Bean 都已创建
        assertNotNull(ctx.getBean(DestroyFirst::class.java))
        assertNotNull(ctx.getBean(DestroySecond::class.java))

        ctx.lifecycleManager.shutdown()

        assertEquals(2, DestroyOrderTracker.order.size)
        // 后创建的先销毁（逆序）
        assertEquals("DestroySecond", DestroyOrderTracker.order[0])
        assertEquals("DestroyFirst", DestroyOrderTracker.order[1])
    }

    @Test
    fun `Lazy singleton 在 initialize 时不创建`() {
        LazyTracker.constructed = false

        val ctx = IocTestContext()
        ctx.register(LazyTrackerBean::class.java)
        ctx.initialize()

        assertFalse(LazyTracker.constructed, "Lazy Bean 不应在 initialize 时创建")

        val bean = ctx.getBean(LazyTrackerBean::class.java)
        assertNotNull(bean)
        assertTrue(LazyTracker.constructed, "首次 getBean 时应创建 Lazy Bean")
    }

    @Test
    fun `PostConstruct 在依赖注入完成后调用`() {
        PostConstructVerifier.depAvailable = false

        val ctx = IocTestContext()
        ctx.register(DepBean::class.java)
        ctx.register(PostConstructVerifierBean::class.java)
        ctx.initialize()

        assertTrue(PostConstructVerifier.depAvailable)
    }

    // ==================== 测试用 Bean ====================

    @Service
    class EagerBean

    object DestroyOrderTracker {
        val order = mutableListOf<String>()
    }

    @Service
    class DestroyFirst {
        @PreDestroy
        fun onDestroy() {
            DestroyOrderTracker.order.add("DestroyFirst")
        }
    }

    @Service
    class DestroySecond @Inject constructor(
        private val first: DestroyFirst
    ) {
        @PreDestroy
        fun onDestroy() {
            DestroyOrderTracker.order.add("DestroySecond")
        }
    }

    object LazyTracker {
        var constructed = false
    }

    @Component
    @Lazy
    class LazyTrackerBean {
        init {
            LazyTracker.constructed = true
        }
    }

    @Service
    class DepBean

    object PostConstructVerifier {
        var depAvailable = false
    }

    @Service
    class PostConstructVerifierBean {
        @Inject
        lateinit var dep: DepBean

        @PostConstruct
        fun onInit() {
            PostConstructVerifier.depAvailable = this::dep.isInitialized
        }
    }
}

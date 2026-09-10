package top.wcpe.taboolib.ioc.lifecycle

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import top.wcpe.taboolib.ioc.IocTestContext
import top.wcpe.taboolib.ioc.annotation.Component
import top.wcpe.taboolib.ioc.annotation.Inject
import top.wcpe.taboolib.ioc.annotation.Lazy
import top.wcpe.taboolib.ioc.annotation.PostConstruct
import top.wcpe.taboolib.ioc.cycle.CircularDependencyException
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

/**
 * A-P1-01 防回归护栏：收窄 [LifecycleManager] 单例创建锁的临界区前后，
 * 以下不变量必须保持不变：
 *
 * 1. 构造器循环依赖仍抛出 [CircularDependencyException]（真判据，不得破坏）；
 * 2. 同名单例并发创建仍只产生**一个**实例（double-check 语义不变）；
 * 3. 并发创建不同单例不产生重复实例、不互相破坏。
 *
 * 这些用例在改锁**之前**必须全绿，作为收窄临界区的安全网。
 */
class SingletonCreationConcurrencyTest {

    @BeforeEach
    fun setup() {
        // 使用轻量上下文，避免全局单例状态污染
    }

    @AfterEach
    fun teardown() {
    }

    // ── 1. 构造器循环依赖仍抛异常 ──

    @Test
    fun `构造器循环依赖必须抛出 CircularDependencyException`() {
        val ctx = IocTestContext()
        ctx.register(CtorCycleA::class.java)
        ctx.register(CtorCycleB::class.java)

        assertThrows(CircularDependencyException::class.java) {
            ctx.initialize()
        }
    }

    // ── 2. 同名单例并发创建只产生一个实例 ──

    @Test
    fun `并发解析同名单例只创建一个实例`() {
        val ctx = IocTestContext()
        ctx.register(ConcurrentSingletonBean::class.java)
        // 触发定义注册；@Lazy 单例不会被预创建，多线程并发 getBean 触发真实并发创建
        ctx.initialize()
        ConcurrentSingletonBean.instanceCount.set(0)

        val instances = CopyOnWriteArrayList<Any>()
        val threadCount = 16
        val barrier = CyclicBarrier(threadCount)
        val latch = CountDownLatch(threadCount)

        val threads = (1..threadCount).map {
            thread {
                try {
                    barrier.await(5, TimeUnit.SECONDS)
                    val bean = ctx.getBean(ConcurrentSingletonBean::class.java)
                    if (bean != null) instances.add(bean)
                } finally {
                    latch.countDown()
                }
            }
        }
        assertTrue(latch.await(10, TimeUnit.SECONDS), "所有线程应在超时前完成")
        threads.forEach { it.join(2000) }

        assertEquals(threadCount, instances.size, "每个线程都应拿到实例")
        val first = instances.first()
        assertTrue(instances.all { it === first }, "并发解析同名单例必须返回同一实例")
        assertEquals(1, ConcurrentSingletonBean.instanceCount.get(), "同名单例只应实例化一次")
    }

    // ── 3. 并发创建不同单例不产生重复实例 ──

    @Test
    fun `并发创建不同单例各自只创建一个实例`() {
        val ctx = IocTestContext()
        ctx.register(ConcurrentSingletonBean::class.java)
        ctx.register(AnotherConcurrentBean::class.java)
        ctx.initialize()

        ConcurrentSingletonBean.instanceCount.set(0)
        AnotherConcurrentBean.instanceCount.set(0)

        val threadCount = 16
        val barrier = CyclicBarrier(threadCount)
        val latch = CountDownLatch(threadCount)
        val errors = CopyOnWriteArrayList<Throwable>()

        val threads = (1..threadCount).map { idx ->
            thread {
                try {
                    barrier.await(5, TimeUnit.SECONDS)
                    if (idx % 2 == 0) {
                        ctx.getBean(ConcurrentSingletonBean::class.java)
                    } else {
                        ctx.getBean(AnotherConcurrentBean::class.java)
                    }
                } catch (t: Throwable) {
                    errors.add(t)
                } finally {
                    latch.countDown()
                }
            }
        }
        assertTrue(latch.await(10, TimeUnit.SECONDS), "所有线程应在超时前完成")
        threads.forEach { it.join(2000) }

        assertTrue(errors.isEmpty(), "并发创建不应抛异常: $errors")
        assertEquals(1, ConcurrentSingletonBean.instanceCount.get(), "同名单例只应实例化一次")
        assertEquals(1, AnotherConcurrentBean.instanceCount.get(), "同名单例只应实例化一次")

        // 单例缓存一致性：多次获取必须是同一实例
        assertSame(
            ctx.getBean(ConcurrentSingletonBean::class.java),
            ctx.getBean(ConcurrentSingletonBean::class.java)
        )
    }

    @Test
    fun `并发单例解析后 singletonsInCreation 必须清空`() {
        val ctx = IocTestContext()
        ctx.register(ConcurrentSingletonBean::class.java)
        ctx.initialize()

        val threadCount = 8
        val barrier = CyclicBarrier(threadCount)
        val latch = CountDownLatch(threadCount)
        val threads = (1..threadCount).map {
            thread {
                try {
                    barrier.await(5, TimeUnit.SECONDS)
                    ctx.getBean(ConcurrentSingletonBean::class.java)
                } finally {
                    latch.countDown()
                }
            }
        }
        assertTrue(latch.await(10, TimeUnit.SECONDS))
        threads.forEach { it.join(2000) }

        val field = ctx.lifecycleManager.javaClass.getDeclaredField("singletonsInCreation")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val inCreation = field.get(ctx.lifecycleManager) as Set<String>
        assertEquals(0, inCreation.size, "并发创建完成后 singletonsInCreation 不应残留")
    }

    // ── 4. 收窄临界区：@PostConstruct 阻塞不得串行化其他线程的解析（A-P1-01） ──

    @Test
    fun `@PostConstruct 阻塞期间其他线程不应被全局锁串行化`() {
        val ctx = IocTestContext()
        ctx.register(BlockingPostConstructBean::class.java)
        ctx.register(ConcurrentSingletonBean::class.java)
        ctx.initialize()

        val postConstructEntered = CountDownLatch(1)
        val releasePostConstruct = CountDownLatch(1)
        BlockingPostConstructBean.enteredLatch = postConstructEntered
        BlockingPostConstructBean.releaseLatch = releasePostConstruct

        // 线程 T1：触发阻塞 Bean 的构建（进入 @PostConstruct 后长时间阻塞）
        val t1 = thread {
            ctx.getBean(BlockingPostConstructBean::class.java)
        }
        assertTrue(
            postConstructEntered.await(5, TimeUnit.SECONDS),
            "T1 应进入 @PostConstruct"
        )

        // 线程 T2：在 T1 的 @PostConstruct 阻塞期间解析**另一个**单例；
        // 若全局锁覆盖整个 createBean（含 @PostConstruct），T2 会被串行化卡住直至超时。
        val t2Completed = CountDownLatch(1)
        val t2 = thread {
            try {
                ctx.getBean(ConcurrentSingletonBean::class.java)
            } finally {
                t2Completed.countDown()
            }
        }

        val t2Done = t2Completed.await(5, TimeUnit.SECONDS)
        // 释放 T1，避免测试悬挂
        releasePostConstruct.countDown()
        t1.join(5000)
        t2.join(5000)

        assertTrue(
            t2Done,
            "T1 的 @PostConstruct 阻塞期间，T2 解析其他单例不应被全局锁串行化（A-P1-01）"
        )
    }

    // ==================== 测试夹具 ====================

    @Component
    class CtorCycleA @Inject constructor(val b: CtorCycleB)

    @Component
    class CtorCycleB @Inject constructor(val a: CtorCycleA)

    @Component
    @Lazy
    class ConcurrentSingletonBean {
        companion object {
            val instanceCount = AtomicInteger(0)
        }

        init {
            instanceCount.incrementAndGet()
        }

        fun ping(): String = "pong"
    }

    @Component
    @Lazy
    class AnotherConcurrentBean {
        companion object {
            val instanceCount = AtomicInteger(0)
        }

        init {
            instanceCount.incrementAndGet()
        }
    }

    /** @Lazy 单例，其 @PostConstruct 会阻塞到测试显式释放，用于验证锁已收窄。 */
    @Component
    @Lazy
    class BlockingPostConstructBean {
        companion object {
            @Volatile
            var enteredLatch: CountDownLatch? = null

            @Volatile
            var releaseLatch: CountDownLatch? = null
        }

        @PostConstruct
        fun init() {
            enteredLatch?.countDown()
            releaseLatch?.await(5, TimeUnit.SECONDS)
        }
    }
}

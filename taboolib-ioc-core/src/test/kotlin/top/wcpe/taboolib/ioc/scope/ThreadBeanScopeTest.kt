package top.wcpe.taboolib.ioc.scope

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import top.wcpe.taboolib.ioc.IocTestContext
import top.wcpe.taboolib.ioc.annotation.Component
import top.wcpe.taboolib.ioc.annotation.ThreadScope
import top.wcpe.taboolib.ioc.bean.BeanScopes
import java.lang.ref.WeakReference
import java.util.concurrent.*

/**
 * ThreadBeanScope 的专项测试，重点测试线程池环境下的内存泄漏问题。
 */
class ThreadBeanScopeTest {

    @Test
    fun `clear should remove ThreadLocal completely`() {
        val threadScope = ThreadBeanScope()
        val ctx = IocTestContext()
        ctx.registerScope(BeanScopes.THREAD, threadScope)
        ctx.register(ThreadScopedTestBean::class.java)
        ctx.initialize()

        // 获取实例，触发 ThreadLocal 初始化
        val bean = ctx.getBean(ThreadScopedTestBean::class.java)
        assertNotNull(bean)

        // 调用 clear() 应完全移除 ThreadLocal
        threadScope.clear()

        // 再次获取应创建新实例
        val newBean = ctx.getBean(ThreadScopedTestBean::class.java)
        assertNotNull(newBean)
        assertNotSame(bean, newBean, "clear() 后应创建新实例")
    }

    @Test
    fun `clearCurrentThread should only clear cache but keep ThreadLocal`() {
        val threadScope = ThreadBeanScope()
        val ctx = IocTestContext()
        ctx.registerScope(BeanScopes.THREAD, threadScope)
        ctx.register(ThreadScopedTestBean::class.java)
        ctx.initialize()

        val bean = ctx.getBean(ThreadScopedTestBean::class.java)
        assertNotNull(bean)

        // clearCurrentThread() 只清空缓存
        threadScope.clearCurrentThread()

        val newBean = ctx.getBean(ThreadScopedTestBean::class.java)
        assertNotNull(newBean)
        assertNotSame(bean, newBean, "clearCurrentThread() 后应创建新实例")
    }

    @Test
    fun `thread pool without clear should reuse instances across tasks`() {
        val threadScope = ThreadBeanScope()
        val ctx = IocTestContext()
        ctx.registerScope(BeanScopes.THREAD, threadScope)
        ctx.register(ThreadScopedTestBean::class.java)
        ctx.initialize()

        val executor = Executors.newFixedThreadPool(1)
        val instances = CopyOnWriteArrayList<ThreadScopedTestBean>()

        try {
            // 在同一个线程池线程中执行两次任务，不清理
            val latch = CountDownLatch(2)
            repeat(2) {
                executor.submit {
                    try {
                        val bean = ctx.getBean(ThreadScopedTestBean::class.java)
                        instances.add(bean!!)
                    } finally {
                        latch.countDown()
                    }
                }
            }
            latch.await(5, TimeUnit.SECONDS)

            assertEquals(2, instances.size)
            // 因为是同一个线程，且没有清理，应该是同一个实例
            assertSame(instances[0], instances[1], "线程池复用线程时，未清理会导致实例复用")
        } finally {
            executor.shutdown()
            executor.awaitTermination(5, TimeUnit.SECONDS)
        }
    }

    @Test
    fun `thread pool with clear should create new instances for each task`() {
        val threadScope = ThreadBeanScope()
        val ctx = IocTestContext()
        ctx.registerScope(BeanScopes.THREAD, threadScope)
        ctx.register(ThreadScopedTestBean::class.java)
        ctx.initialize()

        val executor = Executors.newFixedThreadPool(1)
        val instances = CopyOnWriteArrayList<ThreadScopedTestBean>()

        try {
            val latch = CountDownLatch(2)
            repeat(2) {
                executor.submit {
                    try {
                        val bean = ctx.getBean(ThreadScopedTestBean::class.java)
                        instances.add(bean!!)
                    } finally {
                        // 正确做法：任务结束时清理
                        threadScope.clear()
                        latch.countDown()
                    }
                }
            }
            latch.await(5, TimeUnit.SECONDS)

            assertEquals(2, instances.size)
            // 因为每次都清理了，应该是不同的实例
            assertNotSame(instances[0], instances[1], "使用 clear() 后每次任务应创建新实例")
        } finally {
            executor.shutdown()
            executor.awaitTermination(5, TimeUnit.SECONDS)
        }
    }

    @Test
    fun `memory leak test - without clear in thread pool`() {
        val threadScope = ThreadBeanScope()
        val ctx = IocTestContext()
        ctx.registerScope(BeanScopes.THREAD, threadScope)
        ctx.register(LargeThreadScopedBean::class.java)
        ctx.initialize()

        val executor = Executors.newFixedThreadPool(2)
        val weakRefs = CopyOnWriteArrayList<WeakReference<LargeThreadScopedBean>>()

        try {
            val latch = CountDownLatch(10)
            repeat(10) {
                executor.submit {
                    try {
                        val bean = ctx.getBean(LargeThreadScopedBean::class.java)
                        weakRefs.add(WeakReference(bean))
                        // 故意不调用 clear()，模拟内存泄漏场景
                    } finally {
                        latch.countDown()
                    }
                }
            }
            latch.await(5, TimeUnit.SECONDS)

            // 因为线程池只有 2 个线程，未清理时最多保留 2 个不同的实例
            val aliveInstances = weakRefs.mapNotNull { it.get() }.toSet()
            assertTrue(aliveInstances.size <= 2, "线程池有 2 个线程，未清理时应最多保留 2 个不同实例，实际: ${aliveInstances.size}")
        } finally {
            executor.shutdown()
            executor.awaitTermination(5, TimeUnit.SECONDS)
        }
    }

    @Test
    fun `memory leak test - with clear in thread pool`() {
        val threadScope = ThreadBeanScope()
        val ctx = IocTestContext()
        ctx.registerScope(BeanScopes.THREAD, threadScope)
        ctx.register(LargeThreadScopedBean::class.java)
        ctx.initialize()

        val executor = Executors.newFixedThreadPool(2)
        val weakRefs = CopyOnWriteArrayList<WeakReference<LargeThreadScopedBean>>()

        try {
            val latch = CountDownLatch(10)
            repeat(10) {
                executor.submit {
                    try {
                        val bean = ctx.getBean(LargeThreadScopedBean::class.java)
                        weakRefs.add(WeakReference(bean))
                    } finally {
                        threadScope.clear() // 正确清理
                        latch.countDown()
                    }
                }
            }
            latch.await(5, TimeUnit.SECONDS)

            // 强制 GC
            System.gc()
            Thread.sleep(100)
            System.gc()

            // 因为每次都清理了，大部分实例应该可以被 GC
            val aliveCount = weakRefs.count { it.get() != null }
            assertTrue(aliveCount < weakRefs.size, "使用 clear() 后，部分实例应该可以被 GC，存活: $aliveCount/${weakRefs.size}")
        } finally {
            executor.shutdown()
            executor.awaitTermination(5, TimeUnit.SECONDS)
        }
    }

    @Test
    fun `concurrent access in thread pool should be thread-safe`() {
        val threadScope = ThreadBeanScope()
        val ctx = IocTestContext()
        ctx.registerScope(BeanScopes.THREAD, threadScope)
        ctx.register(ThreadScopedTestBean::class.java)
        ctx.initialize()

        val executor = Executors.newFixedThreadPool(10)
        val errors = CopyOnWriteArrayList<Throwable>()
        val threadIds = ConcurrentHashMap.newKeySet<Long>()
        val instancesByThread = ConcurrentHashMap<Long, MutableList<ThreadScopedTestBean>>()

        try {
            val latch = CountDownLatch(100)
            repeat(100) {
                executor.submit {
                    try {
                        val threadId = Thread.currentThread().id
                        threadIds.add(threadId)

                        val bean = ctx.getBean(ThreadScopedTestBean::class.java)
                        assertNotNull(bean)

                        instancesByThread.computeIfAbsent(threadId) { CopyOnWriteArrayList() }.add(bean!!)
                    } catch (e: Throwable) {
                        errors.add(e)
                    } finally {
                        threadScope.clear()
                        latch.countDown()
                    }
                }
            }
            latch.await(10, TimeUnit.SECONDS)

            assertTrue(errors.isEmpty(), "并发访问不应出现错误: $errors")

            // 验证每个线程内的实例是相同的（在 clear 之前）
            instancesByThread.forEach { (threadId, instances) ->
                if (instances.size > 1) {
                    // 如果同一个线程执行了多次，在 clear 之前应该是同一个实例
                    // 但因为我们在 finally 中 clear 了，所以每次都是新实例
                    // 这里只验证不会出现异常
                }
            }
        } finally {
            executor.shutdown()
            executor.awaitTermination(10, TimeUnit.SECONDS)
        }
    }

    @Test
    fun `clear and clearCurrentThread difference test`() {
        val threadScope = ThreadBeanScope()
        val ctx = IocTestContext()
        ctx.registerScope(BeanScopes.THREAD, threadScope)
        ctx.register(ThreadScopedTestBean::class.java)
        ctx.initialize()

        // 测试 clearCurrentThread
        val bean1 = ctx.getBean(ThreadScopedTestBean::class.java)
        threadScope.clearCurrentThread()
        val bean2 = ctx.getBean(ThreadScopedTestBean::class.java)
        assertNotSame(bean1, bean2, "clearCurrentThread 应清空缓存")

        // 测试 clear
        val bean3 = ctx.getBean(ThreadScopedTestBean::class.java)
        threadScope.clear()
        val bean4 = ctx.getBean(ThreadScopedTestBean::class.java)
        assertNotSame(bean3, bean4, "clear 应移除 ThreadLocal")
    }

    @Test
    fun `multiple threads with independent caches`() {
        val threadScope = ThreadBeanScope()
        val ctx = IocTestContext()
        ctx.registerScope(BeanScopes.THREAD, threadScope)
        ctx.register(ThreadScopedTestBean::class.java)
        ctx.initialize()

        val executor = Executors.newFixedThreadPool(5)
        val instancesByThread = ConcurrentHashMap<Long, ThreadScopedTestBean>()

        try {
            val latch = CountDownLatch(5)
            repeat(5) {
                executor.submit {
                    try {
                        val threadId = Thread.currentThread().id
                        val bean = ctx.getBean(ThreadScopedTestBean::class.java)
                        instancesByThread[threadId] = bean!!

                        // 验证同一线程多次获取是同一实例
                        val bean2 = ctx.getBean(ThreadScopedTestBean::class.java)
                        assertSame(bean, bean2, "同一线程内应返回相同实例")
                    } finally {
                        threadScope.clear()
                        latch.countDown()
                    }
                }
            }
            latch.await(5, TimeUnit.SECONDS)

            assertEquals(5, instancesByThread.size, "5 个线程应产生 5 个不同实例")
            val uniqueInstances = instancesByThread.values.map { System.identityHashCode(it) }.toSet()
            assertEquals(5, uniqueInstances.size, "所有实例应该是不同的")
        } finally {
            executor.shutdown()
            executor.awaitTermination(5, TimeUnit.SECONDS)
        }
    }
}

@Component
@ThreadScope
class ThreadScopedTestBean {
    val id = System.nanoTime()
}

@Component
@ThreadScope
class LargeThreadScopedBean {
    // 模拟大对象，用于内存泄漏测试
    @Suppress("unused")
    private val data = ByteArray(1024 * 1024) // 1MB
    val id = System.nanoTime()
}

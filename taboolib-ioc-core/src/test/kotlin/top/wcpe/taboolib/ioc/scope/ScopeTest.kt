package top.wcpe.taboolib.ioc.scope

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import top.wcpe.taboolib.ioc.IocTestContext
import top.wcpe.taboolib.ioc.annotation.Component
import top.wcpe.taboolib.ioc.annotation.Inject
import top.wcpe.taboolib.ioc.annotation.RefreshScope
import top.wcpe.taboolib.ioc.annotation.ThreadScope
import top.wcpe.taboolib.ioc.bean.BeanScopes
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch

class ScopeTest {

    @Test
    fun `thread scope should return same instance in same thread`() {
        val ctx = IocTestContext()
        ctx.registerScope(BeanScopes.THREAD, ThreadBeanScope())
        ctx.register(ThreadScopedBean::class.java)
        ctx.initialize()

        val first = ctx.getBean(ThreadScopedBean::class.java)
        val second = ctx.getBean(ThreadScopedBean::class.java)

        assertNotNull(first)
        assertSame(first, second)
    }

    @Test
    fun `thread scope should return different instances in different threads`() {
        val ctx = IocTestContext()
        ctx.registerScope(BeanScopes.THREAD, ThreadBeanScope())
        ctx.register(ThreadScopedBean::class.java)
        ctx.initialize()

        val mainInstance = ctx.getBean(ThreadScopedBean::class.java)
        var otherInstance: ThreadScopedBean? = null

        val thread = Thread {
            otherInstance = ctx.getBean(ThreadScopedBean::class.java)
        }
        thread.start()
        thread.join()

        assertNotNull(mainInstance)
        assertNotNull(otherInstance)
        assertNotSame(mainInstance, otherInstance)
    }

    @Test
    fun `refresh scope should return same instance until refreshed`() {
        val ctx = IocTestContext()
        val refreshScope = RefreshBeanScope(ctx.registry)
        ctx.registerScope(BeanScopes.REFRESH, refreshScope)
        ctx.register(RefreshScopedBean::class.java)
        ctx.initialize()

        val first = ctx.getBean(RefreshScopedBean::class.java)
        val second = ctx.getBean(RefreshScopedBean::class.java)
        assertSame(first, second)

        // 刷新后应返回新实例
        refreshScope.refresh()
        val third = ctx.getBean(RefreshScopedBean::class.java)
        assertNotNull(third)
        assertNotSame(first, third)
    }

    @Test
    fun `refresh scope should support refreshing by name`() {
        val ctx = IocTestContext()
        val refreshScope = RefreshBeanScope(ctx.registry)
        ctx.registerScope(BeanScopes.REFRESH, refreshScope)
        ctx.register(RefreshScopedBean::class.java)
        ctx.initialize()

        val first = ctx.getBean(RefreshScopedBean::class.java)

        refreshScope.refresh("refreshScopedBean")
        val second = ctx.getBean(RefreshScopedBean::class.java)

        assertNotNull(first)
        assertNotNull(second)
        assertNotSame(first, second)
    }

    @Test
    fun `thread scope clear should remove cached instances`() {
        val threadScope = ThreadBeanScope()
        val ctx = IocTestContext()
        ctx.registerScope(BeanScopes.THREAD, threadScope)
        ctx.register(ThreadScopedBean::class.java)
        ctx.initialize()

        val first = ctx.getBean(ThreadScopedBean::class.java)
        threadScope.clearCurrentThread()
        val second = ctx.getBean(ThreadScopedBean::class.java)

        assertNotNull(first)
        assertNotNull(second)
        assertNotSame(first, second)
    }

    // ========== 新增测试用例 ==========

    @Test
    fun `concurrent thread scope access should produce unique instances per thread`() {
        val ctx = IocTestContext()
        ctx.registerScope(BeanScopes.THREAD, ThreadBeanScope())
        ctx.register(ThreadScopedBean::class.java)
        ctx.initialize()

        val threadCount = 10
        val latch = CountDownLatch(threadCount)
        val instances = CopyOnWriteArrayList<ThreadScopedBean>()

        repeat(threadCount) {
            Thread {
                try {
                    val bean = ctx.getBean(ThreadScopedBean::class.java)
                    assertNotNull(bean)
                    instances.add(bean!!)
                } finally {
                    latch.countDown()
                }
            }.start()
        }
        latch.await()

        assertEquals(threadCount, instances.size)
        val uniqueInstances = instances.map { System.identityHashCode(it) }.toSet()
        assertEquals(threadCount, uniqueInstances.size, "每个线程应获得不同的实例")
    }

    @Test
    fun `refresh scope concurrent read during refresh should not throw`() {
        val ctx = IocTestContext()
        val refreshScope = RefreshBeanScope(ctx.registry)
        ctx.registerScope(BeanScopes.REFRESH, refreshScope)
        ctx.register(RefreshScopedBean::class.java)
        ctx.initialize()

        // 先获取一次确保缓存存在
        ctx.getBean(RefreshScopedBean::class.java)

        val threadCount = 10
        val latch = CountDownLatch(threadCount)
        val errors = CopyOnWriteArrayList<Throwable>()

        repeat(threadCount) { i ->
            Thread {
                try {
                    if (i % 2 == 0) {
                        refreshScope.refresh()
                    }
                    val bean = ctx.getBean(RefreshScopedBean::class.java)
                    assertNotNull(bean)
                } catch (e: Throwable) {
                    errors.add(e)
                } finally {
                    latch.countDown()
                }
            }.start()
        }
        latch.await()

        assertTrue(errors.isEmpty(), "并发刷新和读取不应抛出异常，但出现: $errors")
    }

    @Test
    fun `thread scope bean should have dependency injected`() {
        val ctx = IocTestContext()
        ctx.registerScope(BeanScopes.THREAD, ThreadBeanScope())
        ctx.register(SingletonDep::class.java)
        ctx.register(ThreadScopedWithDep::class.java)
        ctx.initialize()

        val bean = ctx.getBean(ThreadScopedWithDep::class.java)
        assertNotNull(bean)
        assertNotNull(bean!!.dep, "ThreadScope Bean 的依赖应被注入")
        assertSame(ctx.getBean(SingletonDep::class.java), bean.dep)
    }

    @Test
    fun `refresh scope bean should have dependency injected after refresh`() {
        val ctx = IocTestContext()
        val refreshScope = RefreshBeanScope(ctx.registry)
        ctx.registerScope(BeanScopes.REFRESH, refreshScope)
        ctx.register(SingletonDep::class.java)
        ctx.register(RefreshScopedWithDep::class.java)
        ctx.initialize()

        val first = ctx.getBean(RefreshScopedWithDep::class.java)
        assertNotNull(first)
        assertNotNull(first!!.dep, "RefreshScope Bean 的依赖应被注入")

        val singletonDep = ctx.getBean(SingletonDep::class.java)

        refreshScope.refresh()
        val second = ctx.getBean(RefreshScopedWithDep::class.java)
        assertNotNull(second)
        assertNotSame(first, second, "刷新后应返回新实例")
        assertNotNull(second!!.dep, "刷新后新实例的依赖也应被注入")
        assertSame(singletonDep, second.dep, "刷新后依赖应仍为同一个 singleton")
    }

    @Test
    fun `refresh by non-existent name should not error`() {
        val ctx = IocTestContext()
        val refreshScope = RefreshBeanScope(ctx.registry)
        assertDoesNotThrow {
            refreshScope.refresh("nonExistent")
        }
    }

    @Test
    fun `refresh scope clear should remove all cached instances`() {
        val ctx = IocTestContext()
        val refreshScope = RefreshBeanScope(ctx.registry)
        ctx.registerScope(BeanScopes.REFRESH, refreshScope)
        ctx.register(RefreshScopedBean::class.java)
        ctx.initialize()

        val first = ctx.getBean(RefreshScopedBean::class.java)
        assertNotNull(first)

        refreshScope.clear()
        val second = ctx.getBean(RefreshScopedBean::class.java)
        assertNotNull(second)
        assertNotSame(first, second, "clear() 后应返回新实例")
    }

    @Test
    fun `BeanScopes isBuiltin should return true for all builtin scopes`() {
        assertTrue(BeanScopes.isBuiltin("thread"))
        assertTrue(BeanScopes.isBuiltin("refresh"))
        assertTrue(BeanScopes.isBuiltin("singleton"))
        assertTrue(BeanScopes.isBuiltin("prototype"))
    }

    @Test
    fun `BeanScopes isBuiltin should return false for custom scopes`() {
        assertFalse(BeanScopes.isBuiltin("custom"))
    }

    @Test
    fun `BeanScopes isStandard should return false for thread and refresh`() {
        assertFalse(BeanScopes.isStandard("thread"))
        assertFalse(BeanScopes.isStandard("refresh"))
    }
}

@Component
@ThreadScope
class ThreadScopedBean

@Component
@RefreshScope
class RefreshScopedBean

@Component
class SingletonDep

@Component
@ThreadScope
class ThreadScopedWithDep {
    @Inject
    lateinit var dep: SingletonDep
}

@Component
@RefreshScope
class RefreshScopedWithDep {
    @Inject
    lateinit var dep: SingletonDep
}

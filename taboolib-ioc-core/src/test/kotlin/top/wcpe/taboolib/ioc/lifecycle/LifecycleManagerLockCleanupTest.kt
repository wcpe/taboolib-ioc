package top.wcpe.taboolib.ioc.lifecycle

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import top.wcpe.taboolib.ioc.IocTestContext
import top.wcpe.taboolib.ioc.annotation.Service
import java.lang.reflect.Field

/**
 * LifecycleManager 锁对象清理测试
 * 
 * 验证 singletonLocks 在 shutdown 后被正确清理，防止内存泄漏
 */
class LifecycleManagerLockCleanupTest {

    @Test
    fun `singletonLocks 在 Bean 初始化后包含锁对象`() {
        val ctx = IocTestContext()
        ctx.register(TestBean1::class.java)
        ctx.register(TestBean2::class.java)
        ctx.register(TestBean3::class.java)
        
        ctx.initialize()
        
        val locksSize = getSingletonLocksSize(ctx)
        assertTrue(locksSize >= 3, "singletonLocks 应包含至少 3 个锁对象，实际: $locksSize")
    }

    @Test
    fun `singletonLocks 在 shutdown 后被清空`() {
        val ctx = IocTestContext()
        ctx.register(TestBean1::class.java)
        ctx.register(TestBean2::class.java)
        ctx.register(TestBean3::class.java)
        
        ctx.initialize()
        
        val locksBeforeShutdown = getSingletonLocksSize(ctx)
        assertTrue(locksBeforeShutdown > 0, "shutdown 前应有锁对象")
        
        ctx.shutdown()
        
        val locksAfterShutdown = getSingletonLocksSize(ctx)
        assertEquals(0, locksAfterShutdown, "shutdown 后 singletonLocks 应被清空")
    }

    @Test
    fun `多次初始化和关闭不会导致锁对象累积`() {
        val ctx = IocTestContext()
        ctx.register(TestBean1::class.java)
        ctx.register(TestBean2::class.java)
        
        // 第一次初始化和关闭
        ctx.initialize()
        val locksAfterFirstInit = getSingletonLocksSize(ctx)
        assertTrue(locksAfterFirstInit > 0, "第一次初始化后应有锁对象")
        
        ctx.shutdown()
        assertEquals(0, getSingletonLocksSize(ctx), "第一次 shutdown 后应清空")
        
        // 第二次初始化和关闭
        ctx.initialize()
        val locksAfterSecondInit = getSingletonLocksSize(ctx)
        assertTrue(locksAfterSecondInit > 0, "第二次初始化后应有锁对象")
        assertEquals(locksAfterFirstInit, locksAfterSecondInit, "两次初始化的锁对象数量应相同")
        
        ctx.shutdown()
        assertEquals(0, getSingletonLocksSize(ctx), "第二次 shutdown 后应清空")
        
        // 第三次初始化和关闭
        ctx.initialize()
        val locksAfterThirdInit = getSingletonLocksSize(ctx)
        assertTrue(locksAfterThirdInit > 0, "第三次初始化后应有锁对象")
        
        ctx.shutdown()
        assertEquals(0, getSingletonLocksSize(ctx), "第三次 shutdown 后应清空")
    }

    @Test
    fun `大量 Bean 初始化后 shutdown 能正确清理所有锁`() {
        val ctx = IocTestContext()
        
        // 注册多个 Bean
        ctx.register(TestBean1::class.java)
        ctx.register(TestBean2::class.java)
        ctx.register(TestBean3::class.java)
        ctx.register(TestBean4::class.java)
        ctx.register(TestBean5::class.java)
        ctx.register(TestBean6::class.java)
        ctx.register(TestBean7::class.java)
        ctx.register(TestBean8::class.java)
        ctx.register(TestBean9::class.java)
        ctx.register(TestBean10::class.java)
        
        ctx.initialize()
        
        val locksBeforeShutdown = getSingletonLocksSize(ctx)
        assertTrue(locksBeforeShutdown >= 10, "应至少有 10 个锁对象，实际: $locksBeforeShutdown")
        
        ctx.shutdown()
        
        assertEquals(0, getSingletonLocksSize(ctx), "shutdown 后所有锁对象应被清空")
    }

    @Test
    fun `resetState 清理所有内部状态包括锁对象`() {
        val ctx = IocTestContext()
        ctx.register(TestBean1::class.java)
        ctx.register(TestBean2::class.java)
        
        ctx.initialize()
        
        assertTrue(getSingletonLocksSize(ctx) > 0, "初始化后应有锁对象")
        assertTrue(getInitializationOrderSize(ctx) > 0, "初始化后应有初始化顺序记录")
        assertTrue(getInitializedSingletonsSize(ctx) > 0, "初始化后应有已初始化单例记录")
        
        ctx.lifecycleManager.resetState()
        
        assertEquals(0, getSingletonLocksSize(ctx), "resetState 后锁对象应被清空")
        assertEquals(0, getInitializationOrderSize(ctx), "resetState 后初始化顺序应被清空")
        assertEquals(0, getInitializedSingletonsSize(ctx), "resetState 后已初始化单例应被清空")
    }

    // ==================== 辅助方法 ====================

    private fun getSingletonLocksSize(ctx: IocTestContext): Int {
        val field = getPrivateField(ctx.lifecycleManager, "singletonLocks")
        @Suppress("UNCHECKED_CAST")
        val locks = field.get(ctx.lifecycleManager) as java.util.concurrent.ConcurrentHashMap<String, Any>
        return locks.size
    }

    private fun getInitializationOrderSize(ctx: IocTestContext): Int {
        val field = getPrivateField(ctx.lifecycleManager, "initializationOrder")
        @Suppress("UNCHECKED_CAST")
        val order = field.get(ctx.lifecycleManager) as List<String>
        return order.size
    }

    private fun getInitializedSingletonsSize(ctx: IocTestContext): Int {
        val field = getPrivateField(ctx.lifecycleManager, "initializedSingletons")
        @Suppress("UNCHECKED_CAST")
        val singletons = field.get(ctx.lifecycleManager) as java.util.concurrent.ConcurrentHashMap.KeySetView<String, Boolean>
        return singletons.size
    }

    private fun getPrivateField(obj: Any, fieldName: String): Field {
        val field = obj.javaClass.getDeclaredField(fieldName)
        field.isAccessible = true
        return field
    }

    // ==================== 测试用 Bean ====================

    @Service
    class TestBean1

    @Service
    class TestBean2

    @Service
    class TestBean3

    @Service
    class TestBean4

    @Service
    class TestBean5

    @Service
    class TestBean6

    @Service
    class TestBean7

    @Service
    class TestBean8

    @Service
    class TestBean9

    @Service
    class TestBean10
}

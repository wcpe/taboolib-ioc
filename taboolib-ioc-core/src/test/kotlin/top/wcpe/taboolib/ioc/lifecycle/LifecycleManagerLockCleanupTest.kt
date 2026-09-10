package top.wcpe.taboolib.ioc.lifecycle

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import top.wcpe.taboolib.ioc.IocTestContext
import top.wcpe.taboolib.ioc.annotation.Service
import java.lang.reflect.Field

/**
 * LifecycleManager 单例创建状态清理测试
 *
 * 单例创建已从「每 Bean 一把锁」改为「单一全局创建锁 + singletonsInCreation 集合」
 * （修复循环依赖 + 多线程下的锁顺序死锁）。
 *
 * 本测试验证新实现的内部状态卫生：
 * - singletonsInCreation 在创建完成后必须为空（不能残留创建标记，否则后续创建会被误判为循环依赖）
 * - resetState / shutdown 必须清空 initializationOrder / initializedSingletons，防止内存泄漏
 * - 多次 initialize/shutdown 周期不会累积状态
 */
class LifecycleManagerLockCleanupTest {

    @Test
    fun `singletonsInCreation 在 Bean 初始化完成后为空`() {
        val ctx = IocTestContext()
        ctx.register(TestBean1::class.java)
        ctx.register(TestBean2::class.java)
        ctx.register(TestBean3::class.java)

        ctx.initialize()

        assertEquals(0, getSingletonsInCreationSize(ctx), "初始化完成后 singletonsInCreation 不应残留创建标记")
    }

    @Test
    fun `initializationOrder 在 shutdown 后被清空`() {
        val ctx = IocTestContext()
        ctx.register(TestBean1::class.java)
        ctx.register(TestBean2::class.java)
        ctx.register(TestBean3::class.java)

        ctx.initialize()

        val orderBeforeShutdown = getInitializationOrderSize(ctx)
        assertTrue(orderBeforeShutdown > 0, "shutdown 前应有初始化顺序记录")

        ctx.shutdown()

        assertEquals(0, getInitializationOrderSize(ctx), "shutdown 后初始化顺序应被清空")
        assertEquals(0, getSingletonsInCreationSize(ctx), "shutdown 后 singletonsInCreation 不应残留")
    }

    @Test
    fun `多次初始化和关闭不会导致状态累积`() {
        val ctx = IocTestContext()
        ctx.register(TestBean1::class.java)
        ctx.register(TestBean2::class.java)

        // 第一次初始化和关闭
        ctx.initialize()
        val orderAfterFirstInit = getInitializationOrderSize(ctx)
        assertTrue(orderAfterFirstInit > 0, "第一次初始化后应有初始化顺序记录")

        ctx.shutdown()
        assertEquals(0, getInitializationOrderSize(ctx), "第一次 shutdown 后应清空")

        // 第二次初始化和关闭
        ctx.initialize()
        val orderAfterSecondInit = getInitializationOrderSize(ctx)
        assertEquals(orderAfterFirstInit, orderAfterSecondInit, "两次初始化的记录数量应相同")
        assertEquals(0, getSingletonsInCreationSize(ctx), "第二次初始化后 singletonsInCreation 不应残留")

        ctx.shutdown()
        assertEquals(0, getInitializationOrderSize(ctx), "第二次 shutdown 后应清空")

        // 第三次初始化和关闭
        ctx.initialize()
        assertTrue(getInitializationOrderSize(ctx) > 0, "第三次初始化后应有初始化顺序记录")

        ctx.shutdown()
        assertEquals(0, getInitializationOrderSize(ctx), "第三次 shutdown 后应清空")
        assertEquals(0, getSingletonsInCreationSize(ctx), "第三次 shutdown 后 singletonsInCreation 不应残留")
    }

    @Test
    fun `大量 Bean 初始化后 shutdown 能正确清理所有状态`() {
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

        val orderBeforeShutdown = getInitializationOrderSize(ctx)
        assertTrue(orderBeforeShutdown >= 10, "应至少有 10 个初始化记录，实际: $orderBeforeShutdown")
        assertEquals(0, getSingletonsInCreationSize(ctx), "初始化完成后 singletonsInCreation 不应残留")

        ctx.shutdown()

        assertEquals(0, getInitializationOrderSize(ctx), "shutdown 后所有初始化记录应被清空")
        assertEquals(0, getSingletonsInCreationSize(ctx), "shutdown 后 singletonsInCreation 不应残留")
    }

    @Test
    fun `resetState 清理所有内部状态`() {
        val ctx = IocTestContext()
        ctx.register(TestBean1::class.java)
        ctx.register(TestBean2::class.java)

        ctx.initialize()

        assertTrue(getInitializationOrderSize(ctx) > 0, "初始化后应有初始化顺序记录")
        assertTrue(getInitializedSingletonsSize(ctx) > 0, "初始化后应有已初始化单例记录")

        ctx.lifecycleManager.resetState()

        assertEquals(0, getSingletonsInCreationSize(ctx), "resetState 后 singletonsInCreation 应被清空")
        assertEquals(0, getInitializationOrderSize(ctx), "resetState 后初始化顺序应被清空")
        assertEquals(0, getInitializedSingletonsSize(ctx), "resetState 后已初始化单例应被清空")
    }

    // ==================== 辅助方法 ====================

    private fun getSingletonsInCreationSize(ctx: IocTestContext): Int {
        val field = getPrivateField(ctx.lifecycleManager, "singletonsInCreation")
        @Suppress("UNCHECKED_CAST")
        val inCreation = field.get(ctx.lifecycleManager) as LinkedHashSet<String>
        return inCreation.size
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

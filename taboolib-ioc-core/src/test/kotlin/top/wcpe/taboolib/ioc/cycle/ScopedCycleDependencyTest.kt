package top.wcpe.taboolib.ioc.cycle

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import top.wcpe.taboolib.ioc.IocTestContext
import top.wcpe.taboolib.ioc.annotation.Component
import top.wcpe.taboolib.ioc.annotation.Inject
import top.wcpe.taboolib.ioc.annotation.Scope
import top.wcpe.taboolib.ioc.bean.BeanDefinition
import top.wcpe.taboolib.ioc.bean.BeanScope
import java.util.concurrent.ConcurrentHashMap

/**
 * 测试 Prototype 和自定义作用域的循环依赖检测
 */
class ScopedCycleDependencyTest {

    @Test
    fun `Prototype Bean 循环依赖在初始化时不抛出异常`() {
        val ctx = IocTestContext()
        ctx.register(PrototypeCycleA::class.java)
        ctx.register(PrototypeCycleB::class.java)

        // 初始化时应该检测到循环依赖并警告，但不抛出异常
        assertDoesNotThrow { ctx.initialize() }
    }

    @Test
    fun `Prototype Bean 构造函数循环依赖运行时抛出异常`() {
        val ctx = IocTestContext()
        ctx.register(PrototypeCycleA::class.java)
        ctx.register(PrototypeCycleB::class.java)
        ctx.initialize()

        // 尝试获取 Bean 时应该抛出循环依赖异常
        val ex = assertThrows(CircularDependencyException::class.java) {
            ctx.getBean(PrototypeCycleA::class.java)
        }

        assertTrue(ex.dependencyChain.size >= 3, "依赖链应该至少包含 3 个元素")
        assertEquals(ex.dependencyChain.first(), ex.dependencyChain.last(), "依赖链首尾应该相同")
    }

    @Test
    fun `自定义作用域循环依赖在初始化时不抛出异常`() {
        val ctx = IocTestContext()
        ctx.registerScope("custom", SimpleCustomScope())
        ctx.register(CustomScopeCycleA::class.java)
        ctx.register(CustomScopeCycleB::class.java)

        // 初始化时应该检测到循环依赖并警告，但不抛出异常
        assertDoesNotThrow { ctx.initialize() }
    }

    @Test
    fun `混合作用域循环依赖在初始化时抛出异常`() {
        val ctx = IocTestContext()
        ctx.register(MixedScopeCycleA::class.java) // singleton
        ctx.register(MixedScopeCycleB::class.java) // prototype

        // 混合作用域的循环依赖：singleton 依赖 prototype，prototype 依赖 singleton
        // 由于 singleton 会在初始化时预创建，会触发循环依赖异常
        assertThrows(CircularDependencyException::class.java) {
            ctx.initialize()
        }
    }

    @Test
    fun `Singleton Bean 循环依赖可以解析`() {
        val ctx = IocTestContext()
        ctx.register(SingletonCycleA::class.java)
        ctx.register(SingletonCycleB::class.java)

        // Singleton 循环依赖应该可以通过早期暴露解析
        assertDoesNotThrow { ctx.initialize() }

        val a = ctx.getBean(SingletonCycleA::class.java)
        val b = ctx.getBean(SingletonCycleB::class.java)
        assertNotNull(a)
        assertNotNull(b)
        assertSame(b, a!!.b)
        assertSame(a, b!!.a)
    }

    // ==================== 测试用 Bean ====================

    @Component
    @Scope("prototype")
    class PrototypeCycleA @Inject constructor(val b: PrototypeCycleB)

    @Component
    @Scope("prototype")
    class PrototypeCycleB @Inject constructor(val a: PrototypeCycleA)

    @Component
    @Scope("custom")
    class CustomScopeCycleA @Inject constructor(val b: CustomScopeCycleB)

    @Component
    @Scope("custom")
    class CustomScopeCycleB @Inject constructor(val a: CustomScopeCycleA)

    @Component // singleton (默认)
    class MixedScopeCycleA @Inject constructor(val b: MixedScopeCycleB)

    @Component
    @Scope("prototype")
    class MixedScopeCycleB @Inject constructor(val a: MixedScopeCycleA)

    @Component // singleton (默认)
    class SingletonCycleA {
        @Inject
        lateinit var b: SingletonCycleB
    }

    @Component // singleton (默认)
    class SingletonCycleB {
        @Inject
        lateinit var a: SingletonCycleA
    }

    // ==================== 辅助方法 ====================

    /**
     * 简单的自定义作用域实现（每次都创建新实例）
     */
    class SimpleCustomScope : BeanScope {
        private val instances = ConcurrentHashMap<String, Any>()

        override fun get(name: String, definition: BeanDefinition, creator: () -> Any): Any {
            return instances.computeIfAbsent(name) { creator() }
        }

        override fun clear() {
            instances.clear()
        }
    }
}

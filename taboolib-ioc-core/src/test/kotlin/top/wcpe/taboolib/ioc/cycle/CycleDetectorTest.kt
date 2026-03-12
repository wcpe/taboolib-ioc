package top.wcpe.taboolib.ioc.cycle

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import top.wcpe.taboolib.ioc.IocTestContext
import top.wcpe.taboolib.ioc.annotation.*

/**
 * CycleDetector 测试
 */
class CycleDetectorTest {

    @Test
    fun `无环图正常通过`() {
        val ctx = IocTestContext()
        ctx.register(NodeA::class.java)
        ctx.register(NodeB::class.java)
        ctx.register(NodeC::class.java)
        assertDoesNotThrow { ctx.initialize() }

        assertNotNull(ctx.getBean(NodeC::class.java))
    }

    @Test
    fun `字段循环依赖可解析`() {
        val ctx = IocTestContext()
        ctx.register(FieldCycleA::class.java)
        ctx.register(FieldCycleB::class.java)
        assertDoesNotThrow { ctx.initialize() }

        val a = ctx.getBean(FieldCycleA::class.java)
        val b = ctx.getBean(FieldCycleB::class.java)
        assertNotNull(a)
        assertNotNull(b)
        assertSame(b, a!!.b)
        assertSame(a, b!!.a)
    }

    @Test
    fun `构造函数循环依赖抛出异常`() {
        val ctx = IocTestContext()
        ctx.register(CtorCycleP::class.java)
        ctx.register(CtorCycleQ::class.java)

        val ex = assertThrows(CircularDependencyException::class.java) {
            ctx.initialize()
        }
        assertTrue(ex.dependencyChain.size >= 3)
        assertEquals(ex.dependencyChain.first(), ex.dependencyChain.last())
    }

    @Test
    fun `多节点构造函数环检测`() {
        val ctx = IocTestContext()
        ctx.register(TriCycleA::class.java)
        ctx.register(TriCycleB::class.java)
        ctx.register(TriCycleC::class.java)

        assertThrows(CircularDependencyException::class.java) {
            ctx.initialize()
        }
    }

    // ==================== 测试用 Bean ====================

    // 无环图: C -> B -> A
    @Component
    class NodeA

    @Component
    class NodeB @Inject constructor(val a: NodeA)

    @Component
    class NodeC @Inject constructor(val b: NodeB)

    // 字段循环: A <-> B
    @Component
    class FieldCycleA {
        @Inject
        lateinit var b: FieldCycleB
    }

    @Component
    class FieldCycleB {
        @Inject
        lateinit var a: FieldCycleA
    }

    // 构造函数循环: P <-> Q
    @Component
    class CtorCycleP @Inject constructor(val q: CtorCycleQ)

    @Component
    class CtorCycleQ @Inject constructor(val p: CtorCycleP)

    // 三节点构造函数环: A -> B -> C -> A
    @Component
    class TriCycleA @Inject constructor(val b: TriCycleB)

    @Component
    class TriCycleB @Inject constructor(val c: TriCycleC)

    @Component
    class TriCycleC @Inject constructor(val a: TriCycleA)
}

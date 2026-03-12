package top.wcpe.taboolib.ioc.inject

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import top.wcpe.taboolib.ioc.IocTestContext
import top.wcpe.taboolib.ioc.annotation.*

/**
 * ConstructorResolver 测试
 */
class ConstructorResolverTest {

    @Test
    fun `Inject 标注的构造函数优先`() {
        val ctx = IocTestContext()
        ctx.register(CtorDepA::class.java)
        ctx.register(MultiCtorBean::class.java)
        ctx.initialize()

        val bean = ctx.getBean(MultiCtorBean::class.java)
        assertNotNull(bean)
        assertEquals("injected", bean!!.source)
    }

    @Test
    fun `唯一构造函数自动选择`() {
        val ctx = IocTestContext()
        ctx.register(CtorDepA::class.java)
        ctx.register(SingleCtorBean::class.java)
        ctx.initialize()

        val bean = ctx.getBean(SingleCtorBean::class.java)
        assertNotNull(bean)
        assertNotNull(bean!!.dep)
    }

    @Test
    fun `无参构造函数回退`() {
        val ctx = IocTestContext()
        ctx.register(NoArgBean::class.java)
        ctx.initialize()

        val bean = ctx.getBean(NoArgBean::class.java)
        assertNotNull(bean)
    }

    @Test
    fun `Named 构造函数参数注入`() {
        val ctx = IocTestContext()
        ctx.registerBean("greeting", "Hello IoC")
        ctx.register(NamedCtorBean::class.java)
        ctx.initialize()

        val bean = ctx.getBean(NamedCtorBean::class.java)
        assertNotNull(bean)
        assertEquals("Hello IoC", bean!!.message)
    }

    // ==================== 测试用 Bean ====================

    @Component
    class CtorDepA

    @Service
    class MultiCtorBean @Inject constructor(val dep: CtorDepA) {
        val source: String = "injected"

        constructor() : this(CtorDepA()) {
            // 不应被选择
        }
    }

    @Service
    class SingleCtorBean(val dep: CtorDepA)

    @Service
    class NoArgBean

    @Service
    class NamedCtorBean @Inject constructor(
        @Named("greeting") val message: String
    )
}

package top.wcpe.taboolib.ioc.inject

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import top.wcpe.taboolib.ioc.IocTestContext
import top.wcpe.taboolib.ioc.annotation.Component
import top.wcpe.taboolib.ioc.annotation.Inject
import top.wcpe.taboolib.ioc.annotation.Named
import top.wcpe.taboolib.ioc.annotation.Service

/**
 * FieldInjector 测试
 */
class FieldInjectorTest {

    @Test
    fun `Inject 字段注入`() {
        val ctx = IocTestContext()
        ctx.register(FieldDepA::class.java)
        ctx.register(FieldConsumerA::class.java)
        ctx.initialize()

        val consumer = ctx.getBean(FieldConsumerA::class.java)
        assertNotNull(consumer)
        assertNotNull(consumer!!.dep)
    }

    @Test
    fun `Named 字段注入`() {
        val ctx = IocTestContext()
        ctx.register(NamedImplX::class.java)
        ctx.register(NamedImplY::class.java)
        ctx.register(NamedFieldConsumer::class.java)
        ctx.initialize()

        val consumer = ctx.getBean(NamedFieldConsumer::class.java)
        assertNotNull(consumer)
        assertEquals("X", consumer!!.dep.label())
    }

    @Test
    fun `Kotlin lateinit var 注入`() {
        val ctx = IocTestContext()
        ctx.register(FieldDepA::class.java)
        ctx.register(LateinitConsumer::class.java)
        ctx.initialize()

        val consumer = ctx.getBean(LateinitConsumer::class.java)
        assertNotNull(consumer)
    }

    // ==================== 测试用 Bean ====================

    @Component
    class FieldDepA

    @Service
    class FieldConsumerA {
        @Inject
        lateinit var dep: FieldDepA
    }

    interface Labelable {
        fun label(): String
    }

    @Component("implX")
    class NamedImplX : Labelable {
        override fun label() = "X"
    }

    @Component("implY")
    class NamedImplY : Labelable {
        override fun label() = "Y"
    }

    @Service
    class NamedFieldConsumer {
        @Inject
        @Named("implX")
        lateinit var dep: Labelable
    }

    @Service
    class LateinitConsumer {
        @Inject
        lateinit var dep: FieldDepA
    }
}

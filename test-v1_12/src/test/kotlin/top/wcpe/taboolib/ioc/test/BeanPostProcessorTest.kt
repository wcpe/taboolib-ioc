package top.wcpe.taboolib.ioc.test.postprocessor

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import top.wcpe.taboolib.ioc.IocTestContext
import top.wcpe.taboolib.ioc.annotation.*
import top.wcpe.taboolib.ioc.bean.BeanPostProcessor

class BeanPostProcessorTest {

    @Test
    fun `component implementing BeanPostProcessor is auto registered`() {
        val ctx = IocTestContext()
        ctx.register(PpRecorder::class.java); ctx.register(PpBean::class.java); ctx.initialize()
        assertTrue(PpRecorder.before.any { it.endsWith("ppBean") })
        assertTrue(PpRecorder.after.any { it.endsWith("ppBean") })
    }

    @Test
    fun `manually added processor is invoked`() {
        val calls = mutableListOf<String>()
        val ctx = IocTestContext()
        ctx.addBeanPostProcessor(object : BeanPostProcessor {
            override fun postProcessBeforeInitialization(bean: Any, beanName: String): Any {
                calls.add("b:$beanName"); return bean
            }
            override fun postProcessAfterInitialization(bean: Any, beanName: String): Any {
                calls.add("a:$beanName"); return bean
            }
        })
        ctx.register(PpBean::class.java); ctx.initialize()
        assertTrue(calls.contains("b:ppBean"))
        assertTrue(calls.contains("a:ppBean"))
    }

    @Test
    fun `postProcessor can replace instance`() {
        val ctx = IocTestContext()
        ctx.addBeanPostProcessor(object : BeanPostProcessor {
            override fun postProcessAfterInitialization(bean: Any, beanName: String): Any {
                return if (bean is PpReplaceable) PpReplaceable("replaced") else bean
            }
        })
        ctx.register(PpReplaceable::class.java); ctx.initialize()
        val b = ctx.getBean(PpReplaceable::class.java)!!
        assertEquals("replaced", b.tag)
    }

    @Test
    fun `default processor returns same instance`() {
        val p = object : BeanPostProcessor {}
        val b = Any()
        assertSame(b, p.postProcessBeforeInitialization(b, "x"))
        assertSame(b, p.postProcessAfterInitialization(b, "x"))
    }
}

@Component
class PpRecorder : BeanPostProcessor {
    companion object {
        val before = mutableListOf<String>()
        val after = mutableListOf<String>()
    }
    override fun postProcessBeforeInitialization(bean: Any, beanName: String): Any {
        before.add("before:$beanName"); return bean
    }
    override fun postProcessAfterInitialization(bean: Any, beanName: String): Any {
        after.add("after:$beanName"); return bean
    }
}

@Component
class PpBean

@Component
class PpReplaceable(val tag: String = "original")

package top.wcpe.taboolib.ioc.test

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import top.wcpe.taboolib.ioc.IocTestContext
import top.wcpe.taboolib.ioc.annotation.*
import top.wcpe.taboolib.ioc.bean.BeanPostProcessor

/**
 * BeanPostProcessor 测试
 */
class BeanPostProcessorTest {

    @Test
    fun `processor as component is auto-registered and invoked`() {
        BPP_LogProcessor.before.clear()
        BPP_LogProcessor.after.clear()
        val ctx = IocTestContext()
        ctx.register(BPP_LogProcessor::class.java)
        ctx.register(BPP_TargetBean::class.java)
        ctx.initialize()
        assertTrue(BPP_LogProcessor.before.contains("bPP_TargetBean"))
        assertTrue(BPP_LogProcessor.after.contains("bPP_TargetBean"))
    }

    @Test
    fun `before runs prior to postConstruct`() {
        BPP_OrderBean.events.clear()
        val ctx = IocTestContext()
        ctx.register(BPP_HookProcessor::class.java)
        ctx.register(BPP_OrderBean::class.java)
        ctx.initialize()
        // 顺序：before -> postConstruct -> after
        assertEquals("before", BPP_OrderBean.events[0])
        assertEquals("postConstruct", BPP_OrderBean.events[1])
        assertEquals("after", BPP_OrderBean.events[2])
    }

    @Test
    fun `multiple processors chained`() {
        BPP_FirstProcessor.calls = 0
        BPP_SecondProcessor.calls = 0
        val ctx = IocTestContext()
        ctx.register(BPP_FirstProcessor::class.java)
        ctx.register(BPP_SecondProcessor::class.java)
        ctx.register(BPP_TargetBean::class.java)
        ctx.initialize()
        assertTrue(BPP_FirstProcessor.calls > 0)
        assertTrue(BPP_SecondProcessor.calls > 0)
    }

    @Test
    fun `addBeanPostProcessor manually works`() {
        var seen = false
        val ctx = IocTestContext()
        ctx.addBeanPostProcessor(object : BeanPostProcessor {
            override fun postProcessAfterInitialization(bean: Any, beanName: String): Any {
                if (beanName == "bPP_TargetBean") seen = true
                return bean
            }
        })
        ctx.register(BPP_TargetBean::class.java)
        ctx.initialize()
        assertTrue(seen)
    }
}

@Component
class BPP_TargetBean

@Component
class BPP_LogProcessor : BeanPostProcessor {
    companion object {
        val before = mutableListOf<String>()
        val after = mutableListOf<String>()
    }

    override fun postProcessBeforeInitialization(bean: Any, beanName: String): Any {
        before.add(beanName)
        return bean
    }

    override fun postProcessAfterInitialization(bean: Any, beanName: String): Any {
        after.add(beanName)
        return bean
    }
}

@Component
class BPP_OrderBean {
    companion object {
        val events = mutableListOf<String>()
    }

    @PostConstruct
    fun init() {
        events.add("postConstruct")
    }
}

@Component
class BPP_FirstProcessor : BeanPostProcessor {
    companion object {
        var calls = 0
    }

    override fun postProcessAfterInitialization(bean: Any, beanName: String): Any {
        calls++
        return bean
    }
}

@Component
class BPP_SecondProcessor : BeanPostProcessor {
    companion object {
        var calls = 0
    }

    override fun postProcessAfterInitialization(bean: Any, beanName: String): Any {
        calls++
        return bean
    }
}

@Component
class BPP_HookProcessor : BeanPostProcessor {
    override fun postProcessBeforeInitialization(bean: Any, beanName: String): Any {
        if (beanName == "bPP_OrderBean") BPP_OrderBean.events.add("before")
        return bean
    }

    override fun postProcessAfterInitialization(bean: Any, beanName: String): Any {
        if (beanName == "bPP_OrderBean") BPP_OrderBean.events.add("after")
        return bean
    }
}

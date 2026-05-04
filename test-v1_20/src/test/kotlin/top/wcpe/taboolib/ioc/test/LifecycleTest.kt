package top.wcpe.taboolib.ioc.test

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import top.wcpe.taboolib.ioc.IocTestContext
import top.wcpe.taboolib.ioc.annotation.*

/**
 * 生命周期相关注解测试
 */
class LifecycleTest {

    @Test
    fun `postConstruct invoked after injection`() {
        LC_PostCtorBean.invoked = false
        LC_PostCtorBean.depReady = false
        val ctx = IocTestContext()
        ctx.register(LC_Dep::class.java)
        ctx.register(LC_PostCtorBean::class.java)
        ctx.initialize()
        assertTrue(LC_PostCtorBean.invoked)
        assertTrue(LC_PostCtorBean.depReady)
    }

    @Test
    fun `preDestroy invoked on shutdown`() {
        LC_PreDestroyBean.destroyed = false
        val ctx = IocTestContext()
        ctx.register(LC_PreDestroyBean::class.java)
        ctx.initialize()
        assertFalse(LC_PreDestroyBean.destroyed)
        ctx.lifecycleManager.shutdown()
        assertTrue(LC_PreDestroyBean.destroyed)
    }

    @Test
    fun `postEnable invoked once after invokePostEnable`() {
        LC_PostEnableBean.calls = 0
        val ctx = IocTestContext()
        ctx.register(LC_PostEnableBean::class.java)
        ctx.initialize()
        ctx.invokePostEnable()
        assertEquals(1, LC_PostEnableBean.calls)
    }

    @Test
    fun `postEnable not invoked before invokePostEnable`() {
        LC_PostEnableBean.calls = 0
        val ctx = IocTestContext()
        ctx.register(LC_PostEnableBean::class.java)
        ctx.initialize()
        assertEquals(0, LC_PostEnableBean.calls)
    }

    @Test
    fun `dependsOn enforces initialization order`() {
        LC_DepsOrder.order.clear()
        val ctx = IocTestContext()
        ctx.register(LC_FirstBean::class.java)
        ctx.register(LC_SecondBean::class.java)
        ctx.initialize()
        assertEquals(listOf("first", "second"), LC_DepsOrder.order)
    }

    @Test
    fun `postConstruct not invoked twice on second access`() {
        LC_PostCtorCounter.count = 0
        val ctx = IocTestContext()
        ctx.register(LC_PostCtorCounter::class.java)
        ctx.initialize()
        ctx.getBean(LC_PostCtorCounter::class.java)
        ctx.getBean(LC_PostCtorCounter::class.java)
        assertEquals(1, LC_PostCtorCounter.count)
    }

    @Test
    fun `shutdown twice should not double-destroy`() {
        LC_PreDestroyBean.destroyed = false
        LC_PreDestroyBean.destroyCount = 0
        val ctx = IocTestContext()
        ctx.register(LC_PreDestroyBean::class.java)
        ctx.initialize()
        ctx.lifecycleManager.shutdown()
        ctx.lifecycleManager.shutdown()
        assertEquals(1, LC_PreDestroyBean.destroyCount)
    }

    @Test
    fun `multiple postConstruct on same bean all invoked`() {
        LC_MultiPostBean.invokedA = false
        LC_MultiPostBean.invokedB = false
        val ctx = IocTestContext()
        ctx.register(LC_MultiPostBean::class.java)
        ctx.initialize()
        assertTrue(LC_MultiPostBean.invokedA)
        assertTrue(LC_MultiPostBean.invokedB)
    }

    @Test
    fun `postConstruct then postEnable in order`() {
        LC_OrderBean.order.clear()
        val ctx = IocTestContext()
        ctx.register(LC_OrderBean::class.java)
        ctx.initialize()
        ctx.invokePostEnable()
        assertEquals(listOf("post", "enable"), LC_OrderBean.order)
    }

    @Test
    fun `multiple beans postEnable invoked once each`() {
        LC_PostEnableBean.calls = 0
        LC_OrderBean.order.clear()
        val ctx = IocTestContext()
        ctx.register(LC_PostEnableBean::class.java)
        ctx.register(LC_OrderBean::class.java)
        ctx.initialize()
        ctx.invokePostEnable()
        assertEquals(1, LC_PostEnableBean.calls)
        assertTrue(LC_OrderBean.order.contains("enable"))
    }
}

@Component
class LC_Dep {
    fun ping() = "ping"
}

@Component
class LC_PostCtorBean {
    companion object {
        var invoked = false
        var depReady = false
    }

    @Inject
    lateinit var dep: LC_Dep

    @PostConstruct
    fun init() {
        invoked = true
        depReady = this::dep.isInitialized
    }
}

@Component
class LC_PreDestroyBean {
    companion object {
        var destroyed = false
        var destroyCount = 0
    }

    @PreDestroy
    fun destroy() {
        destroyed = true
        destroyCount++
    }
}

@Component
class LC_PostEnableBean {
    companion object {
        var calls = 0
    }

    @PostEnable
    fun onEnable() {
        calls++
    }
}

object LC_DepsOrder {
    val order = mutableListOf<String>()
}

@Component("firstBean")
class LC_FirstBean {
    @PostConstruct
    fun init() {
        LC_DepsOrder.order.add("first")
    }
}

@Component("secondBean")
@DependsOn("firstBean")
class LC_SecondBean {
    @PostConstruct
    fun init() {
        LC_DepsOrder.order.add("second")
    }
}

@Component
class LC_PostCtorCounter {
    companion object {
        var count = 0
    }

    @PostConstruct
    fun init() {
        count++
    }
}

@Component
class LC_MultiPostBean {
    companion object {
        var invokedA = false
        var invokedB = false
    }

    @PostConstruct
    fun a() {
        invokedA = true
    }

    @PostConstruct
    fun b() {
        invokedB = true
    }
}

@Component
class LC_OrderBean {
    companion object {
        val order = mutableListOf<String>()
    }

    @PostConstruct
    fun postCtor() {
        order.add("post")
    }

    @PostEnable
    fun postEnable() {
        order.add("enable")
    }
}

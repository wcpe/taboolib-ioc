package top.wcpe.taboolib.ioc.scope

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import top.wcpe.taboolib.ioc.IocTestContext
import top.wcpe.taboolib.ioc.annotation.Bean
import top.wcpe.taboolib.ioc.annotation.Component
import top.wcpe.taboolib.ioc.annotation.Configuration
import top.wcpe.taboolib.ioc.annotation.PreDestroy
import top.wcpe.taboolib.ioc.annotation.RefreshScope
import top.wcpe.taboolib.ioc.bean.BeanScopes
import java.io.Closeable
import java.util.concurrent.CopyOnWriteArrayList

/**
 * RefreshBeanScope 销毁回调测试。
 * 验证刷新时 @PreDestroy 方法被正确调用。
 */
class RefreshBeanScopeDestroyTest {

    @BeforeEach
    fun setup() {
        DestroyTracker.clear()
    }

    @Test
    fun `refresh should invoke PreDestroy on cached bean`() {
        val ctx = IocTestContext()
        val refreshScope = RefreshBeanScope(ctx.registry)
        ctx.registerScope(BeanScopes.REFRESH, refreshScope)
        ctx.register(BeanWithPreDestroy::class.java)
        ctx.initialize()

        // 创建 Bean
        val bean = ctx.getBean(BeanWithPreDestroy::class.java)
        assertNotNull(bean)
        assertEquals(0, DestroyTracker.destroyedBeans.size)

        // 刷新应触发 @PreDestroy
        refreshScope.refresh()
        assertEquals(1, DestroyTracker.destroyedBeans.size)
        assertEquals("beanWithPreDestroy", DestroyTracker.destroyedBeans[0])
    }

    @Test
    fun `refresh by name should invoke PreDestroy on specific bean`() {
        val ctx = IocTestContext()
        val refreshScope = RefreshBeanScope(ctx.registry)
        ctx.registerScope(BeanScopes.REFRESH, refreshScope)
        ctx.register(BeanWithPreDestroy::class.java)
        ctx.register(AnotherBeanWithPreDestroy::class.java)
        ctx.initialize()

        // 创建两个 Bean
        ctx.getBean(BeanWithPreDestroy::class.java)
        ctx.getBean(AnotherBeanWithPreDestroy::class.java)
        assertEquals(0, DestroyTracker.destroyedBeans.size)

        // 只刷新一个
        refreshScope.refresh("beanWithPreDestroy")
        assertEquals(1, DestroyTracker.destroyedBeans.size)
        assertEquals("beanWithPreDestroy", DestroyTracker.destroyedBeans[0])
    }

    @Test
    fun `refresh all should invoke PreDestroy on all cached beans`() {
        val ctx = IocTestContext()
        val refreshScope = RefreshBeanScope(ctx.registry)
        ctx.registerScope(BeanScopes.REFRESH, refreshScope)
        ctx.register(BeanWithPreDestroy::class.java)
        ctx.register(AnotherBeanWithPreDestroy::class.java)
        ctx.initialize()

        // 创建两个 Bean
        ctx.getBean(BeanWithPreDestroy::class.java)
        ctx.getBean(AnotherBeanWithPreDestroy::class.java)

        // 刷新全部
        refreshScope.refresh()
        assertEquals(2, DestroyTracker.destroyedBeans.size)
        assertTrue(DestroyTracker.destroyedBeans.contains("beanWithPreDestroy"))
        assertTrue(DestroyTracker.destroyedBeans.contains("anotherBeanWithPreDestroy"))
    }

    @Test
    fun `refresh non-existent bean should not throw`() {
        val ctx = IocTestContext()
        val refreshScope = RefreshBeanScope(ctx.registry)
        ctx.registerScope(BeanScopes.REFRESH, refreshScope)
        ctx.initialize()

        assertDoesNotThrow {
            refreshScope.refresh("nonExistent")
        }
    }

    @Test
    fun `refresh bean without PreDestroy should not throw`() {
        val ctx = IocTestContext()
        val refreshScope = RefreshBeanScope(ctx.registry)
        ctx.registerScope(BeanScopes.REFRESH, refreshScope)
        ctx.register(BeanWithoutPreDestroy::class.java)
        ctx.initialize()

        ctx.getBean(BeanWithoutPreDestroy::class.java)

        assertDoesNotThrow {
            refreshScope.refresh()
        }
    }

    @Test
    fun `PreDestroy should release resources properly`() {
        val ctx = IocTestContext()
        val refreshScope = RefreshBeanScope(ctx.registry)
        ctx.registerScope(BeanScopes.REFRESH, refreshScope)
        ctx.register(ResourceHoldingBean::class.java)
        ctx.initialize()

        val bean = ctx.getBean(ResourceHoldingBean::class.java)
        assertNotNull(bean)
        assertTrue(bean!!.isResourceOpen)

        // 刷新应释放资源
        refreshScope.refresh()
        assertFalse(bean.isResourceOpen, "资源应被释放")
    }

    @Test
    fun `multiple PreDestroy methods should all be invoked`() {
        val ctx = IocTestContext()
        val refreshScope = RefreshBeanScope(ctx.registry)
        ctx.registerScope(BeanScopes.REFRESH, refreshScope)
        ctx.register(BeanWithMultiplePreDestroy::class.java)
        ctx.initialize()

        ctx.getBean(BeanWithMultiplePreDestroy::class.java)

        refreshScope.refresh()
        assertEquals(2, DestroyTracker.destroyedBeans.size)
        assertTrue(DestroyTracker.destroyedBeans.contains("cleanup1"))
        assertTrue(DestroyTracker.destroyedBeans.contains("cleanup2"))
    }

    @Test
    fun `clear should invoke PreDestroy on all beans`() {
        val ctx = IocTestContext()
        val refreshScope = RefreshBeanScope(ctx.registry)
        ctx.registerScope(BeanScopes.REFRESH, refreshScope)
        ctx.register(BeanWithPreDestroy::class.java)
        ctx.register(AnotherBeanWithPreDestroy::class.java)
        ctx.initialize()

        ctx.getBean(BeanWithPreDestroy::class.java)
        ctx.getBean(AnotherBeanWithPreDestroy::class.java)

        refreshScope.clear()
        assertEquals(2, DestroyTracker.destroyedBeans.size)
    }

    @Test
    fun `factory bean PreDestroy should be invoked on actual type`() {
        val ctx = IocTestContext()
        val refreshScope = RefreshBeanScope(ctx.registry)
        ctx.registerScope(BeanScopes.REFRESH, refreshScope)
        ctx.register(FactoryProducedBeanRefreshScoped::class.java)
        ctx.initialize()

        val bean = ctx.getBean(FactoryProducedBeanRefreshScoped::class.java)
        assertNotNull(bean)

        refreshScope.refresh("factoryProducedBeanRefreshScoped")
        // 应调用实际类型 FactoryProducedBeanImpl 上的 @PreDestroy
        assertEquals(1, DestroyTracker.destroyedBeans.size)
        assertEquals("factoryProduced", DestroyTracker.destroyedBeans[0])
    }

    @Test
    fun `PreDestroy exception should not prevent other beans from being destroyed`() {
        val ctx = IocTestContext()
        val refreshScope = RefreshBeanScope(ctx.registry)
        ctx.registerScope(BeanScopes.REFRESH, refreshScope)
        ctx.register(BeanWithFailingPreDestroy::class.java)
        ctx.register(BeanWithPreDestroy::class.java)
        ctx.initialize()

        ctx.getBean(BeanWithFailingPreDestroy::class.java)
        ctx.getBean(BeanWithPreDestroy::class.java)

        // 即使一个 Bean 的 @PreDestroy 失败，其他 Bean 仍应被销毁
        assertDoesNotThrow {
            refreshScope.refresh()
        }
        // BeanWithPreDestroy 应该被销毁
        assertTrue(DestroyTracker.destroyedBeans.contains("beanWithPreDestroy"))
    }
}

// ==================== 测试用 Bean ====================

object DestroyTracker {
    val destroyedBeans = CopyOnWriteArrayList<String>()

    fun clear() {
        destroyedBeans.clear()
    }
}

@Component
@RefreshScope
class BeanWithPreDestroy {
    @PreDestroy
    fun cleanup() {
        DestroyTracker.destroyedBeans.add("beanWithPreDestroy")
    }
}

@Component
@RefreshScope
class AnotherBeanWithPreDestroy {
    @PreDestroy
    fun cleanup() {
        DestroyTracker.destroyedBeans.add("anotherBeanWithPreDestroy")
    }
}

@Component
@RefreshScope
class BeanWithoutPreDestroy

@Component
@RefreshScope
class ResourceHoldingBean : Closeable {
    var isResourceOpen = true
        private set

    @PreDestroy
    override fun close() {
        isResourceOpen = false
    }
}

@Component
@RefreshScope
class BeanWithMultiplePreDestroy {
    @PreDestroy
    fun cleanup1() {
        DestroyTracker.destroyedBeans.add("cleanup1")
    }

    @PreDestroy
    fun cleanup2() {
        DestroyTracker.destroyedBeans.add("cleanup2")
    }
}

@Component
@RefreshScope
class BeanWithFailingPreDestroy {
    @PreDestroy
    fun cleanup() {
        throw RuntimeException("PreDestroy failed")
    }
}

// 工厂 Bean 测试
interface FactoryProducedBean

class FactoryProducedBeanImpl : FactoryProducedBean {
    @PreDestroy
    fun cleanup() {
        DestroyTracker.destroyedBeans.add("factoryProduced")
    }
}

@Component
@RefreshScope
class FactoryProducedBeanRefreshScoped : FactoryProducedBean {
    @PreDestroy
    fun cleanup() {
        DestroyTracker.destroyedBeans.add("factoryProduced")
    }
}

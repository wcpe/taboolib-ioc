package top.wcpe.taboolib.ioc.scan

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import top.wcpe.taboolib.ioc.IocTestContext
import top.wcpe.taboolib.ioc.annotation.*

/**
 * 第二批修复测试：生命周期与注入增强
 * - TODO-5: 支持多个 @PostConstruct/@PostEnable/@PreDestroy 方法
 * - TODO-6: @Inject 增加 required 参数
 */
class Batch2LifecycleInjectTest {

    // ═══════════════════════════════════════════════════════════════
    // TODO-5: 支持多个 @PostConstruct/@PostEnable/@PreDestroy 方法
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun `multiple @PostConstruct methods should all be called`() {
        MultiLifecycleTracker.reset()
        val ctx = IocTestContext()
        ctx.register(MultiLifecycleComponent::class.java)
        ctx.initialize()

        assertTrue(MultiLifecycleTracker.initACalled, "initA 应被调用")
        assertTrue(MultiLifecycleTracker.initBCalled, "initB 应被调用")
    }

    @Test
    fun `multiple @PostEnable methods should all be called`() {
        MultiLifecycleTracker.reset()
        val ctx = IocTestContext()
        ctx.register(MultiLifecycleComponent::class.java)
        ctx.initialize()
        ctx.invokePostEnable()

        assertTrue(MultiLifecycleTracker.enableACalled, "enableA 应被调用")
        assertTrue(MultiLifecycleTracker.enableBCalled, "enableB 应被调用")
    }

    @Test
    fun `multiple @PreDestroy methods should all be called`() {
        MultiLifecycleTracker.reset()
        val ctx = IocTestContext()
        ctx.register(MultiLifecycleComponent::class.java)
        ctx.initialize()
        ctx.lifecycleManager.shutdown()

        assertTrue(MultiLifecycleTracker.destroyACalled, "destroyA 应被调用")
        assertTrue(MultiLifecycleTracker.destroyBCalled, "destroyB 应被调用")
    }

    @Test
    fun `BeanDefinition should have multiple lifecycle methods in lists`() {
        val ctx = IocTestContext()
        ctx.register(MultiLifecycleComponent::class.java)

        val definition = ctx.registry.getAll().first { it.name == "multiLifecycleComponent" }
        assertEquals(2, definition.postConstructMethods.size, "应有 2 个 @PostConstruct 方法")
        assertEquals(2, definition.postEnableMethods.size, "应有 2 个 @PostEnable 方法")
        assertEquals(2, definition.preDestroyMethods.size, "应有 2 个 @PreDestroy 方法")
        // 兼容性：单字段仍取第一个
        assertNotNull(definition.postConstruct)
        assertNotNull(definition.postEnable)
        assertNotNull(definition.preDestroy)
    }

    // ═══════════════════════════════════════════════════════════════
    // TODO-6: @Inject 增加 required 参数
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun `required inject should throw when dependency missing`() {
        val ctx = IocTestContext()
        ctx.register(RequiredInjectComponent::class.java)

        assertThrows<Exception> {
            ctx.initialize()
        }
    }

    @Test
    fun `optional inject should not throw when dependency missing`() {
        val ctx = IocTestContext()
        ctx.register(OptionalInjectComponent::class.java)
        ctx.initialize()

        val bean = ctx.getBean(OptionalInjectComponent::class.java)
        assertNotNull(bean)
        assertNull(bean!!.optionalDep, "@Inject(required=false) 字段应保持 null")
    }

    @Test
    fun `optional inject should be injected when dependency exists`() {
        val ctx = IocTestContext()
        ctx.register(DepService::class.java)
        ctx.register(OptionalInjectComponent::class.java)
        ctx.initialize()

        val bean = ctx.getBean(OptionalInjectComponent::class.java)
        assertNotNull(bean)
        assertNotNull(bean!!.optionalDep, "依赖存在时 @Inject(required=false) 字段应被注入")
    }
}

// ═══════════════════════════════════════════════════════════════
// 测试用组件
// ═══════════════════════════════════════════════════════════════

// --- TODO-5 测试组件 ---

object MultiLifecycleTracker {
    var initACalled = false
    var initBCalled = false
    var enableACalled = false
    var enableBCalled = false
    var destroyACalled = false
    var destroyBCalled = false

    fun reset() {
        initACalled = false
        initBCalled = false
        enableACalled = false
        enableBCalled = false
        destroyACalled = false
        destroyBCalled = false
    }
}

@Component
class MultiLifecycleComponent {
    @PostConstruct
    fun initA() {
        MultiLifecycleTracker.initACalled = true
    }

    @PostConstruct
    fun initB() {
        MultiLifecycleTracker.initBCalled = true
    }

    @PostEnable
    fun enableA() {
        MultiLifecycleTracker.enableACalled = true
    }

    @PostEnable
    fun enableB() {
        MultiLifecycleTracker.enableBCalled = true
    }

    @PreDestroy
    fun destroyA() {
        MultiLifecycleTracker.destroyACalled = true
    }

    @PreDestroy
    fun destroyB() {
        MultiLifecycleTracker.destroyBCalled = true
    }
}

// --- TODO-6 测试组件 ---

interface DepServiceI {
    fun value(): String
}

@Component
class DepService : DepServiceI {
    override fun value(): String = "dep"
}

@Component
class RequiredInjectComponent {
    @Inject
    lateinit var missingDep: MissingDepType
}

// 一个不会被注册的类型
interface MissingDepType

@Component
class OptionalInjectComponent {
    @Inject(required = false)
    var optionalDep: DepServiceI? = null
}

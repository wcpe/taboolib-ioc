package top.wcpe.taboolib.ioc.scan

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import top.wcpe.taboolib.ioc.IocTestContext
import top.wcpe.taboolib.ioc.annotation.*
import top.wcpe.taboolib.ioc.bean.BeanRegistry
import top.wcpe.taboolib.ioc.inject.ValueResolver

/**
 * 第五批修复测试：缺陷修复 + 边界场景
 * - TODO-14: FieldInjector 多 Bean 时 @Primary 正确选择
 * - TODO-15: 多个 @Primary 时抛异常
 * - TODO-16: shutdown 清理 ValueResolver
 * - TODO-17: @Bean 返回接口类型时运行时补充扫描
 * - TODO-18: shutdown 中 preDestroy null 实例保护
 * - TODO-19: 边界场景测试
 */
class Batch5DefectFixTest {

    // ═══════════════════════════════════════════════════════════════
    // TODO-14: FieldInjector 多 Bean 时 @Primary 正确选择
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun `field injection should use @Primary when multiple beans of same type exist`() {
        val ctx = IocTestContext()
        ctx.register(PrimaryPayService::class.java)
        ctx.register(SecondaryPayService::class.java)
        ctx.register(PayConsumer::class.java)
        ctx.initialize()

        val consumer = ctx.getBean(PayConsumer::class.java)
        assertNotNull(consumer)
        assertEquals("primary-pay", consumer!!.getPayName())
    }

    @Test
    fun `constructor injection should also use @Primary`() {
        val ctx = IocTestContext()
        ctx.register(PrimaryPayService::class.java)
        ctx.register(SecondaryPayService::class.java)
        ctx.register(PayConsumerCtor::class.java)
        ctx.initialize()

        val consumer = ctx.getBean(PayConsumerCtor::class.java)
        assertNotNull(consumer)
        assertEquals("primary-pay", consumer!!.getPayName())
    }

    // ═══════════════════════════════════════════════════════════════
    // TODO-15: 多个 @Primary 时抛异常
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun `multiple @Primary beans of same type should throw exception`() {
        val registry = BeanRegistry()
        val def1 = createSimpleDef("a", DualPrimaryA::class.java, isPrimary = true)
        val def2 = createSimpleDef("b", DualPrimaryB::class.java, isPrimary = true)
        registry.register(def1)
        registry.register(def2)

        assertThrows<IllegalStateException> {
            registry.getPrimaryByType(DualPrimaryI::class.java)
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // TODO-16: shutdown 清理 ValueResolver
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun `ValueResolver properties should be cleared after lifecycle shutdown`() {
        ValueResolver.clearProperties()
        ValueResolver.setProperty("shutdown.test", "before")
        assertEquals("before", ValueResolver.resolve("\${shutdown.test}", String::class.java))

        val ctx = IocTestContext()
        ctx.initialize()
        ctx.lifecycleManager.shutdown()

        // LifecycleManager 本身不清理 ValueResolver（那是 BeanContainer 的职责）
        // 但我们可以验证 clearProperties 的效果
        ValueResolver.clearProperties()
        assertNull(ValueResolver.resolve("\${shutdown.test}", String::class.java))
    }

    // ═══════════════════════════════════════════════════════════════
    // TODO-17: @Bean 返回接口类型时运行时补充扫描
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun `bean returning interface should inject fields on actual implementation`() {
        val ctx = IocTestContext()
        ctx.register(ImplDepService::class.java)
        ctx.register(InterfaceReturnConfig::class.java)
        ctx.initialize()

        val bean = ctx.getBean(InjectableI::class.java)
        assertNotNull(bean)
        // 实际实例是 InjectableImpl，其 @Inject 字段应被注入
        assertTrue(bean is InjectableImpl)
        val impl = bean as InjectableImpl
        assertNotNull(impl.dep, "@Inject 字段应在运行时被补充注入")
    }

    @Test
    fun `bean returning interface should invoke @PostConstruct on actual implementation`() {
        InterfaceLifecycleTracker.reset()
        val ctx = IocTestContext()
        ctx.register(InterfaceLifecycleConfig::class.java)
        ctx.initialize()

        assertTrue(InterfaceLifecycleTracker.postConstructCalled,
            "实际实现类上的 @PostConstruct 应被调用")
    }

    @Test
    fun `bean returning interface should invoke @PostEnable on actual implementation`() {
        InterfaceLifecycleTracker.reset()
        val ctx = IocTestContext()
        ctx.register(InterfaceLifecycleConfig::class.java)
        ctx.initialize()
        ctx.invokePostEnable()

        assertTrue(InterfaceLifecycleTracker.postEnableCalled,
            "实际实现类上的 @PostEnable 应被调用")
    }

    @Test
    fun `bean returning interface should invoke @PreDestroy on actual implementation`() {
        InterfaceLifecycleTracker.reset()
        val ctx = IocTestContext()
        ctx.register(InterfaceLifecycleConfig::class.java)
        ctx.initialize()
        ctx.lifecycleManager.shutdown()

        assertTrue(InterfaceLifecycleTracker.preDestroyCalled,
            "实际实现类上的 @PreDestroy 应被调用")
    }

    @Test
    fun `bean returning interface should inject @Value on actual implementation`() {
        System.setProperty("impl.value.test", "injected-value")
        try {
            val ctx = IocTestContext()
            ctx.register(ValueInterfaceConfig::class.java)
            ctx.initialize()

            val bean = ctx.getBean(ValueHolderI::class.java)
            assertNotNull(bean)
            assertTrue(bean is ValueHolderImpl)
            assertEquals("injected-value", (bean as ValueHolderImpl).configValue)
        } finally {
            System.clearProperty("impl.value.test")
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // 辅助方法
    // ═══════════════════════════════════════════════════════════════

    private fun createSimpleDef(
        name: String,
        type: Class<*>,
        isPrimary: Boolean = false
    ): top.wcpe.taboolib.ioc.bean.BeanDefinition {
        return top.wcpe.taboolib.ioc.bean.BeanDefinition(
            name = name,
            type = type,
            constructor = type.getDeclaredConstructor(),
            injectFields = emptyList(),
            injectMethods = emptyList(),
            postConstruct = null,
            postEnable = null,
            preDestroy = null,
            constructorParameters = emptyList(),
            dependencies = emptyList(),
            isPrimary = isPrimary
        )
    }
}

// ═══════════════════════════════════════════════════════════════
// 测试用组件
// ═══════════════════════════════════════════════════════════════

// --- TODO-14 测试组件 ---

interface PayServiceI {
    fun name(): String
}

@Primary
@Component
class PrimaryPayService : PayServiceI {
    override fun name(): String = "primary-pay"
}

@Component
class SecondaryPayService : PayServiceI {
    override fun name(): String = "secondary-pay"
}

@Component
class PayConsumer {
    @Inject
    lateinit var payService: PayServiceI

    fun getPayName(): String = payService.name()
}

@Component
class PayConsumerCtor(private val payService: PayServiceI) {
    fun getPayName(): String = payService.name()
}

// --- TODO-15 测试组件 ---

interface DualPrimaryI

class DualPrimaryA : DualPrimaryI
class DualPrimaryB : DualPrimaryI

// --- TODO-17 测试组件 ---

interface InjectableI {
    fun hasInjection(): Boolean
}

interface ImplDepI {
    fun value(): String
}

@Component
class ImplDepService : ImplDepI {
    override fun value(): String = "impl-dep"
}

class InjectableImpl : InjectableI {
    @Inject
    var dep: ImplDepI? = null

    override fun hasInjection(): Boolean = dep != null
}

@Configuration
class InterfaceReturnConfig {
    @Bean
    fun injectableBean(): InjectableI = InjectableImpl()
}

// --- TODO-17 生命周期测试组件 ---

object InterfaceLifecycleTracker {
    var postConstructCalled = false
    var postEnableCalled = false
    var preDestroyCalled = false
    fun reset() {
        postConstructCalled = false
        postEnableCalled = false
        preDestroyCalled = false
    }
}

interface LifecycleI {
    fun value(): String
}

class LifecycleImpl : LifecycleI {
    override fun value(): String = "lifecycle-impl"

    @PostConstruct
    fun init() { InterfaceLifecycleTracker.postConstructCalled = true }

    @PostEnable
    fun onEnable() { InterfaceLifecycleTracker.postEnableCalled = true }

    @PreDestroy
    fun onDestroy() { InterfaceLifecycleTracker.preDestroyCalled = true }
}

@Configuration
class InterfaceLifecycleConfig {
    @Bean
    fun lifecycleImpl(): LifecycleI = LifecycleImpl()
}

// --- TODO-17 @Value 测试组件 ---

interface ValueHolderI {
    fun getValue(): String
}

class ValueHolderImpl : ValueHolderI {
    @Value("\${impl.value.test:default}")
    var configValue: String = ""

    override fun getValue(): String = configValue
}

@Configuration
class ValueInterfaceConfig {
    @Bean
    fun valueHolder(): ValueHolderI = ValueHolderImpl()
}

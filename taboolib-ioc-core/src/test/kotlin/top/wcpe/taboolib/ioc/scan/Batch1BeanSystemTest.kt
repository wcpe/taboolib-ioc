package top.wcpe.taboolib.ioc.scan

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import top.wcpe.taboolib.ioc.IocTestContext
import top.wcpe.taboolib.ioc.annotation.*

/**
 * 第一批修复测试：@Bean 系统补全
 * - TODO-1: @Bean 方法参数支持 @Named 限定符
 * - TODO-2: @Bean 产物支持生命周期回调
 * - TODO-3: @Bean 产物支持 @Value/@Inject 字段注入
 * - TODO-4: @Bean 方法级别条件注解支持
 */
class Batch1BeanSystemTest {

    // ═══════════════════════════════════════════════════════════════
    // TODO-1: @Bean 方法参数支持 @Named 限定符
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun `bean method parameter with @Named should resolve by name`() {
        val ctx = IocTestContext()
        ctx.register(FooServiceA::class.java)
        ctx.register(FooServiceB::class.java)
        ctx.register(NamedBeanConfig::class.java)
        ctx.initialize()

        val consumer = ctx.getBean(FooConsumer::class.java)
        assertNotNull(consumer)
        assertEquals("B", consumer!!.getServiceName())
    }

    @Test
    fun `bean method parameter without @Named should resolve by type`() {
        val ctx = IocTestContext()
        ctx.register(SingleFooService::class.java)
        ctx.register(TypeResolveBeanConfig::class.java)
        ctx.initialize()

        val consumer = ctx.getBean(FooConsumer::class.java)
        assertNotNull(consumer)
        assertEquals("single", consumer!!.getServiceName())
    }

    @Test
    fun `scanner should parse @Named qualifier from bean method parameters`() {
        val result = ConfigurationScanner.scan(NamedBeanConfig::class.java, "namedBeanConfig")
        val consumerDef = result.first { it.name == "namedConsumer" }
        assertEquals(1, consumerDef.constructorParameters.size)
        assertEquals("fooB", consumerDef.constructorParameters[0].nameQualifier)
    }

    // ═══════════════════════════════════════════════════════════════
    // TODO-2: @Bean 产物支持生命周期回调
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun `bean product @PostConstruct should be invoked`() {
        LifecycleTracker.reset()
        val ctx = IocTestContext()
        ctx.register(LifecycleBeanConfig::class.java)
        ctx.initialize()

        val bean = ctx.getBean(LifecycleBeanImpl::class.java)
        assertNotNull(bean)
        assertTrue(LifecycleTracker.postConstructCalled, "@PostConstruct 应被调用")
    }

    @Test
    fun `bean product @PostEnable should be invoked`() {
        LifecycleTracker.reset()
        val ctx = IocTestContext()
        ctx.register(LifecycleBeanConfig::class.java)
        ctx.initialize()
        ctx.invokePostEnable()

        assertTrue(LifecycleTracker.postEnableCalled, "@PostEnable 应被调用")
    }

    @Test
    fun `bean product @PreDestroy should be invoked`() {
        LifecycleTracker.reset()
        val ctx = IocTestContext()
        ctx.register(LifecycleBeanConfig::class.java)
        ctx.initialize()
        ctx.lifecycleManager.shutdown()

        assertTrue(LifecycleTracker.preDestroyCalled, "@PreDestroy 应被调用")
    }

    @Test
    fun `scanner should parse lifecycle methods from bean return type`() {
        val result = ConfigurationScanner.scan(LifecycleBeanConfig::class.java, "lifecycleBeanConfig")
        val beanDef = result.first { it.name == "lifecycleBean" }
        assertNotNull(beanDef.postConstruct, "postConstruct 不应为 null")
        assertNotNull(beanDef.postEnable, "postEnable 不应为 null")
        assertNotNull(beanDef.preDestroy, "preDestroy 不应为 null")
    }

    // ═══════════════════════════════════════════════════════════════
    // TODO-3: @Bean 产物支持 @Value/@Inject 字段注入
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun `bean product @Inject field should be injected`() {
        val ctx = IocTestContext()
        ctx.register(InjectTargetService::class.java)
        ctx.register(InjectBeanConfig::class.java)
        ctx.initialize()

        val bean = ctx.getBean(InjectableBean::class.java)
        assertNotNull(bean)
        assertNotNull(bean!!.service, "@Inject 字段应被注入")
        assertEquals("injected", bean.service!!.getName())
    }

    @Test
    fun `bean product @Value field should be injected`() {
        System.setProperty("test.batch1.value", "hello-batch1")
        try {
            val ctx = IocTestContext()
            ctx.register(ValueBeanConfig::class.java)
            ctx.initialize()

            val bean = ctx.getBean(ValueBean::class.java)
            assertNotNull(bean)
            assertEquals("hello-batch1", bean!!.configValue)
        } finally {
            System.clearProperty("test.batch1.value")
        }
    }

    @Test
    fun `scanner should parse inject and value fields from bean return type`() {
        val result = ConfigurationScanner.scan(InjectBeanConfig::class.java, "injectBeanConfig")
        val beanDef = result.first { it.name == "injectableBean" }
        assertTrue(beanDef.injectFields.isNotEmpty(), "injectFields 不应为空")
        assertEquals("service", beanDef.injectFields[0].field.name)
    }

    // ═══════════════════════════════════════════════════════════════
    // TODO-4: @Bean 方法级别条件注解支持
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun `bean method with @ConditionalOnClass should be skipped when class missing`() {
        val ctx = IocTestContext()
        ctx.register(ConditionalBeanConfig::class.java)
        ctx.initialize()

        // missingClassBean 的 @ConditionalOnClass 指定了不存在的类
        assertFalse(ctx.containsBean("missingClassBean"), "条件不满足的 @Bean 不应被注册")
        // alwaysBean 没有条件注解，应该被注册
        assertTrue(ctx.containsBean("alwaysBean"), "无条件的 @Bean 应被注册")
    }

    @Test
    fun `bean method with @ConditionalOnProperty should work`() {
        System.setProperty("feature.enabled", "true")
        try {
            val ctx = IocTestContext()
            ctx.register(PropertyConditionalBeanConfig::class.java)
            ctx.initialize()

            assertTrue(ctx.containsBean("featureBean"), "属性条件满足时 @Bean 应被注册")
        } finally {
            System.clearProperty("feature.enabled")
        }
    }

    @Test
    fun `bean method with @ConditionalOnProperty should be skipped when not matching`() {
        // 不设置属性
        System.clearProperty("feature.enabled")
        val ctx = IocTestContext()
        ctx.register(PropertyConditionalBeanConfig::class.java)
        ctx.initialize()

        assertFalse(ctx.containsBean("featureBean"), "属性条件不满足时 @Bean 不应被注册")
    }
}

// ═══════════════════════════════════════════════════════════════
// 测试用组件
// ═══════════════════════════════════════════════════════════════

// --- TODO-1 测试组件 ---

interface FooService {
    fun getName(): String
}

@Component("fooA")
class FooServiceA : FooService {
    override fun getName(): String = "A"
}

@Component("fooB")
class FooServiceB : FooService {
    override fun getName(): String = "B"
}

@Component
class SingleFooService : FooService {
    override fun getName(): String = "single"
}

class FooConsumer(private val service: FooService) {
    fun getServiceName(): String = service.getName()
}

@Configuration
class NamedBeanConfig {
    @Bean
    fun namedConsumer(@Named("fooB") service: FooService): FooConsumer = FooConsumer(service)
}

@Configuration
class TypeResolveBeanConfig {
    @Bean
    fun typeConsumer(service: FooService): FooConsumer = FooConsumer(service)
}

// --- TODO-2 测试组件 ---

object LifecycleTracker {
    var postConstructCalled = false
    var postEnableCalled = false
    var preDestroyCalled = false

    fun reset() {
        postConstructCalled = false
        postEnableCalled = false
        preDestroyCalled = false
    }
}

interface LifecycleBean {
    fun value(): String
}

class LifecycleBeanImpl : LifecycleBean {
    override fun value(): String = "lifecycle"

    @PostConstruct
    fun init() {
        LifecycleTracker.postConstructCalled = true
    }

    @PostEnable
    fun onEnable() {
        LifecycleTracker.postEnableCalled = true
    }

    @PreDestroy
    fun destroy() {
        LifecycleTracker.preDestroyCalled = true
    }
}

@Configuration
class LifecycleBeanConfig {
    @Bean
    fun lifecycleBean(): LifecycleBeanImpl = LifecycleBeanImpl()
}

// --- TODO-3 测试组件 ---

interface InjectTargetServiceI {
    fun getName(): String
}

@Component
class InjectTargetService : InjectTargetServiceI {
    override fun getName(): String = "injected"
}

class InjectableBean {
    @Inject
    var service: InjectTargetServiceI? = null
}

class ValueBean {
    @Value("\${test.batch1.value:default}")
    var configValue: String = ""
}

@Configuration
class InjectBeanConfig {
    @Bean
    fun injectableBean(): InjectableBean = InjectableBean()
}

@Configuration
class ValueBeanConfig {
    @Bean
    fun valueBean(): ValueBean = ValueBean()
}

// --- TODO-4 测试组件 ---

interface SimpleMarker {
    fun mark(): String
}

class AlwaysMarker : SimpleMarker {
    override fun mark(): String = "always"
}

class MissingClassMarker : SimpleMarker {
    override fun mark(): String = "missing"
}

class FeatureMarker : SimpleMarker {
    override fun mark(): String = "feature"
}

@Configuration
class ConditionalBeanConfig {
    @Bean
    fun alwaysBean(): SimpleMarker = AlwaysMarker()

    @ConditionalOnClass("com.nonexistent.SomeClass")
    @Bean
    fun missingClassBean(): SimpleMarker = MissingClassMarker()
}

@Configuration
class PropertyConditionalBeanConfig {
    @ConditionalOnProperty(name = "feature.enabled", havingValue = "true")
    @Bean
    fun featureBean(): SimpleMarker = FeatureMarker()
}

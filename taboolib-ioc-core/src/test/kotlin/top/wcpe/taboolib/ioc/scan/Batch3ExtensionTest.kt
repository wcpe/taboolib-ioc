package top.wcpe.taboolib.ioc.scan

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import top.wcpe.taboolib.ioc.IocTestContext
import top.wcpe.taboolib.ioc.annotation.*
import top.wcpe.taboolib.ioc.bean.BeanPostProcessor
import top.wcpe.taboolib.ioc.inject.ValueResolver

/**
 * 第三批修复测试：扩展机制
 * - TODO-7: BeanPostProcessor 扩展点
 * - TODO-8: @DependsOn 显式初始化顺序
 * - TODO-9: @Value 支持 @PropertySource
 */
class Batch3ExtensionTest {

    // ═══════════════════════════════════════════════════════════════
    // TODO-7: BeanPostProcessor 扩展点
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun `BeanPostProcessor should be called during bean creation`() {
        PostProcessorTracker.reset()
        val ctx = IocTestContext()
        ctx.register(TrackingPostProcessor::class.java)
        ctx.register(SimpleTarget::class.java)
        ctx.initialize()

        assertTrue(PostProcessorTracker.beforeCalled, "postProcessBeforeInitialization 应被调用")
        assertTrue(PostProcessorTracker.afterCalled, "postProcessAfterInitialization 应被调用")
        assertTrue(PostProcessorTracker.processedBeans.contains("simpleTarget"), "应处理 simpleTarget Bean")
    }

    @Test
    fun `manually added BeanPostProcessor should work`() {
        val tracker = mutableListOf<String>()
        val ctx = IocTestContext()
        ctx.addBeanPostProcessor(object : BeanPostProcessor {
            override fun postProcessBeforeInitialization(bean: Any, beanName: String): Any {
                tracker.add("before:$beanName")
                return bean
            }
            override fun postProcessAfterInitialization(bean: Any, beanName: String): Any {
                tracker.add("after:$beanName")
                return bean
            }
        })
        ctx.register(SimpleTarget::class.java)
        ctx.initialize()

        assertTrue(tracker.contains("before:simpleTarget"))
        assertTrue(tracker.contains("after:simpleTarget"))
    }

    // ═══════════════════════════════════════════════════════════════
    // TODO-8: @DependsOn 显式初始化顺序
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun `@DependsOn should ensure initialization order`() {
        InitOrderTracker.reset()
        val ctx = IocTestContext()
        ctx.register(IndependentBean::class.java)
        ctx.register(DependentBean::class.java)
        ctx.initialize()

        val independentIdx = InitOrderTracker.order.indexOf("independentBean")
        val dependentIdx = InitOrderTracker.order.indexOf("dependentBean")
        assertTrue(independentIdx >= 0, "independentBean 应被初始化")
        assertTrue(dependentIdx >= 0, "dependentBean 应被初始化")
        assertTrue(independentIdx < dependentIdx, "independentBean 应在 dependentBean 之前初始化")
    }

    @Test
    fun `BeanDefinition should have dependsOn field`() {
        val ctx = IocTestContext()
        ctx.register(DependentBean::class.java)

        val definition = ctx.registry.getAll().first { it.name == "dependentBean" }
        assertEquals(listOf("independentBean"), definition.dependsOn)
    }

    // ═══════════════════════════════════════════════════════════════
    // TODO-9: @Value 支持 @PropertySource
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun `ValueResolver should resolve from loaded properties`() {
        ValueResolver.clearProperties()
        ValueResolver.setProperty("test.key", "test-value")
        try {
            val result = ValueResolver.resolve("\${test.key}", String::class.java)
            assertEquals("test-value", result)
        } finally {
            ValueResolver.clearProperties()
        }
    }

    @Test
    fun `loaded properties should take priority over system properties`() {
        ValueResolver.clearProperties()
        System.setProperty("priority.test", "system-value")
        ValueResolver.setProperty("priority.test", "loaded-value")
        try {
            val result = ValueResolver.resolve("\${priority.test}", String::class.java)
            assertEquals("loaded-value", result)
        } finally {
            System.clearProperty("priority.test")
            ValueResolver.clearProperties()
        }
    }

    @Test
    fun `@PropertySource should load properties file`() {
        ValueResolver.clearProperties()
        try {
            val ctx = IocTestContext()
            ctx.register(PropertySourceConfig::class.java)
            ctx.initialize()

            val bean = ctx.getBean(PropValueBean::class.java)
            assertNotNull(bean)
            assertEquals("hello-from-file", bean!!.greeting)
        } finally {
            ValueResolver.clearProperties()
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// 测试用组件
// ═══════════════════════════════════════════════════════════════

// --- TODO-7 测试组件 ---

object PostProcessorTracker {
    var beforeCalled = false
    var afterCalled = false
    val processedBeans = mutableListOf<String>()

    fun reset() {
        beforeCalled = false
        afterCalled = false
        processedBeans.clear()
    }
}

@Component
class TrackingPostProcessor : BeanPostProcessor {
    override fun postProcessBeforeInitialization(bean: Any, beanName: String): Any {
        PostProcessorTracker.beforeCalled = true
        PostProcessorTracker.processedBeans.add(beanName)
        return bean
    }

    override fun postProcessAfterInitialization(bean: Any, beanName: String): Any {
        PostProcessorTracker.afterCalled = true
        return bean
    }
}

@Component
class SimpleTarget {
    fun value(): String = "target"
}

// --- TODO-8 测试组件 ---

object InitOrderTracker {
    val order = mutableListOf<String>()
    fun reset() { order.clear() }
}

@Component
class IndependentBean {
    @PostConstruct
    fun init() {
        InitOrderTracker.order.add("independentBean")
    }
}

@DependsOn("independentBean")
@Component
class DependentBean {
    @PostConstruct
    fun init() {
        InitOrderTracker.order.add("dependentBean")
    }
}

// --- TODO-9 测试组件 ---

class PropValueBean {
    @Value("\${app.greeting:default-greeting}")
    var greeting: String = ""
}

@PropertySource("test-config.properties")
@Configuration
class PropertySourceConfig {
    @Bean
    fun propValueBean(): PropValueBean = PropValueBean()
}

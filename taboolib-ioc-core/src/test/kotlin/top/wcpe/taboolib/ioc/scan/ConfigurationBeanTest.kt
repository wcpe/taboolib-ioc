package top.wcpe.taboolib.ioc.scan

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import top.wcpe.taboolib.ioc.IocTestContext
import top.wcpe.taboolib.ioc.annotation.*

class ConfigurationBeanTest {

    // ── 基本 @Configuration + @Bean 测试 ──

    @Test
    fun `bean method should produce a bean`() {
        val ctx = IocTestContext()
        ctx.register(AppConfig::class.java)
        ctx.initialize()

        val greeter = ctx.getBean(Greeter::class.java)
        assertNotNull(greeter)
        assertEquals("hello", greeter!!.greet())
    }

    @Test
    fun `bean method with custom name should register with that name`() {
        val ctx = IocTestContext()
        ctx.register(AppConfig::class.java)
        ctx.initialize()

        assertTrue(ctx.containsBean("myGreeter"))
    }

    @Test
    fun `bean method default name should be method name`() {
        val ctx = IocTestContext()
        ctx.register(AppConfig::class.java)
        ctx.initialize()

        assertTrue(ctx.containsBean("calculator"))
    }

    @Test
    fun `multiple bean methods should all be registered`() {
        val ctx = IocTestContext()
        ctx.register(AppConfig::class.java)
        ctx.initialize()

        assertNotNull(ctx.getBean(Greeter::class.java))
        assertNotNull(ctx.getBean(Calculator::class.java))
    }

    // ── @Bean 方法带参数（依赖注入）测试 ──

    @Test
    fun `bean method with parameter should resolve dependency`() {
        val ctx = IocTestContext()
        ctx.register(AppConfig::class.java)
        ctx.register(MessageServiceImpl::class.java)
        ctx.initialize()

        val formatter = ctx.getBean(MessageFormatter::class.java)
        assertNotNull(formatter)
        assertEquals("[hello world]", formatter!!.format("hello world"))
    }

    // ── 配置类本身也是 Bean ──

    @Test
    fun `configuration class itself should be a bean`() {
        val ctx = IocTestContext()
        ctx.register(AppConfig::class.java)
        ctx.initialize()

        val config = ctx.getBean(AppConfig::class.java)
        assertNotNull(config)
    }

    // ── @Primary 在 @Bean 上 ──

    @Test
    fun `primary bean method should be preferred`() {
        val ctx = IocTestContext()
        ctx.register(PrimaryBeanConfig::class.java)
        ctx.initialize()

        val service = ctx.getBean(SimpleService::class.java)
        assertNotNull(service)
        assertEquals("primary", service!!.name())
    }

    // ── ConfigurationScanner 单元测试 ──

    @Test
    fun `scanner should return empty for non-configuration class`() {
        val result = ConfigurationScanner.scan(MessageServiceImpl::class.java, "messageServiceImpl")
        assertTrue(result.isEmpty())
    }

    @Test
    fun `scanner should parse bean definitions from configuration class`() {
        val result = ConfigurationScanner.scan(AppConfig::class.java, "appConfig")
        assertTrue(result.isNotEmpty())
        assertTrue(result.any { it.name == "myGreeter" })
        assertTrue(result.any { it.name == "calculator" })
    }

    @Test
    fun `factory bean definition should have correct factory info`() {
        val result = ConfigurationScanner.scan(AppConfig::class.java, "appConfig")
        val greeterDef = result.first { it.name == "myGreeter" }
        assertTrue(greeterDef.isFactoryBean())
        assertEquals("appConfig", greeterDef.factoryBeanName)
        assertEquals("myGreeter", greeterDef.factoryMethod?.name)
        assertNull(greeterDef.constructor)
    }
}

// ── 测试用组件 ──

interface Greeter {
    fun greet(): String
}

class SimpleGreeter : Greeter {
    override fun greet(): String = "hello"
}

interface Calculator {
    fun add(a: Int, b: Int): Int
}

class SimpleCalculator : Calculator {
    override fun add(a: Int, b: Int): Int = a + b
}

interface MessageService {
    fun getMessage(): String
}

@Component
class MessageServiceImpl : MessageService {
    override fun getMessage(): String = "hello world"
}

interface MessageFormatter {
    fun format(msg: String): String
}

class DefaultMessageFormatter(private val service: MessageService) : MessageFormatter {
    override fun format(msg: String): String = "[$msg]"
}

interface SimpleService {
    fun name(): String
}

class PrimaryService : SimpleService {
    override fun name(): String = "primary"
}

class SecondaryService : SimpleService {
    override fun name(): String = "secondary"
}

@Configuration
class AppConfig {

    @Bean("myGreeter")
    fun myGreeter(): Greeter = SimpleGreeter()

    @Bean
    fun calculator(): Calculator = SimpleCalculator()

    @Bean
    fun messageFormatter(service: MessageService): MessageFormatter = DefaultMessageFormatter(service)
}

@Configuration
class PrimaryBeanConfig {

    @Primary
    @Bean
    fun primaryService(): SimpleService = PrimaryService()

    @Bean
    fun secondaryService(): SimpleService = SecondaryService()
}

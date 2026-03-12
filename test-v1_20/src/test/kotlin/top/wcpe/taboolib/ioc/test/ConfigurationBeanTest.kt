package top.wcpe.taboolib.ioc.test

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import top.wcpe.taboolib.ioc.IocTestContext
import top.wcpe.taboolib.ioc.annotation.*
import top.wcpe.taboolib.ioc.bean.MethodInvocation

/**
 * @Configuration + @Bean 功能测试（MockBukkit v1.20 环境）
 */
class ConfigurationBeanTest {

    @Test
    fun `basic bean method should produce a bean`() {
        val ctx = IocTestContext()
        ctx.register(MessageServiceImpl::class.java)
        ctx.register(AppConfig::class.java)
        ctx.initialize()

        val greeter = ctx.getBean(Greeter::class.java)
        assertNotNull(greeter)
        assertEquals("hello", greeter!!.greet())
    }

    @Test
    fun `bean method with custom name should register with that name`() {
        val ctx = IocTestContext()
        ctx.register(MessageServiceImpl::class.java)
        ctx.register(AppConfig::class.java)
        ctx.initialize()

        assertTrue(ctx.containsBean("myGreeter"))
    }

    @Test
    fun `bean method default name should be method name`() {
        val ctx = IocTestContext()
        ctx.register(MessageServiceImpl::class.java)
        ctx.register(AppConfig::class.java)
        ctx.initialize()

        assertTrue(ctx.containsBean("calculator"))
    }

    @Test
    fun `multiple bean methods should all be registered`() {
        val ctx = IocTestContext()
        ctx.register(MessageServiceImpl::class.java)
        ctx.register(AppConfig::class.java)
        ctx.initialize()

        assertNotNull(ctx.getBean(Greeter::class.java))
        assertNotNull(ctx.getBean(Calculator::class.java))
    }

    @Test
    fun `bean method with parameter should resolve dependency`() {
        val ctx = IocTestContext()
        ctx.register(MessageServiceImpl::class.java)
        ctx.register(AppConfig::class.java)
        ctx.initialize()

        val formatter = ctx.getBean(MessageFormatter::class.java)
        assertNotNull(formatter)
        assertEquals("[hello world]", formatter!!.format("hello world"))
    }

    @Test
    fun `configuration class itself should be a bean`() {
        val ctx = IocTestContext()
        ctx.register(MessageServiceImpl::class.java)
        ctx.register(AppConfig::class.java)
        ctx.initialize()

        val config = ctx.getBean(AppConfig::class.java)
        assertNotNull(config)
    }

    @Test
    fun `primary bean method should be preferred`() {
        val ctx = IocTestContext()
        ctx.register(PrimaryBeanConfig::class.java)
        ctx.initialize()

        val service = ctx.getBean(SimpleService::class.java)
        assertNotNull(service)
        assertEquals("primary", service!!.name())
    }

    @Test
    fun `bean produced by factory should be injectable into other beans`() {
        val ctx = IocTestContext()
        ctx.register(MessageServiceImpl::class.java)
        ctx.register(AppConfig::class.java)
        ctx.register(GreeterConsumer::class.java)
        ctx.initialize()

        val consumer = ctx.getBean(GreeterConsumer::class.java)
        assertNotNull(consumer)
        assertEquals("hello", consumer!!.greeter.greet())
    }
}

// ── 测试夹具 ──

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

class PrimaryServiceImpl : SimpleService {
    override fun name(): String = "primary"
}

class SecondaryServiceImpl : SimpleService {
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
    fun primaryService(): SimpleService = PrimaryServiceImpl()

    @Bean
    fun secondaryService(): SimpleService = SecondaryServiceImpl()
}

@Component
class GreeterConsumer {
    @Inject
    lateinit var greeter: Greeter
}

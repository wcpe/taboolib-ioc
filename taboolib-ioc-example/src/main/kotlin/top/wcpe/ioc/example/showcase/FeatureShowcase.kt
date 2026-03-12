package top.wcpe.ioc.example.showcase

import top.wcpe.taboolib.ioc.annotation.*
import top.wcpe.taboolib.ioc.bean.BeanPostProcessor

// ═══════════════════════════════════════════════════════════════
// @Configuration + @Bean 示例
// ═══════════════════════════════════════════════════════════════

interface GreetingService {
    fun greet(name: String): String
}

class SimpleGreetingService : GreetingService {
    override fun greet(name: String): String = "Hello, $name!"
}

class FormalGreetingService : GreetingService {
    override fun greet(name: String): String = "Good day, $name."
}

/**
 * @Configuration 示例：通过 @Bean 方法声明 Bean。
 * 支持 @Primary、@Named 参数、@ConditionalOnProperty 等。
 */
@Configuration
class GreetingConfig {

    /** 默认的问候服务，标记为 @Primary */
    @Primary
    @Bean
    fun simpleGreeting(): GreetingService = SimpleGreetingService()

    /** 正式的问候服务 */
    @Bean("formalGreeting")
    fun formalGreeting(): GreetingService = FormalGreetingService()

    /** @Bean 方法参数支持 @Named 限定符 */
    @Bean
    fun greetingFacade(@Named("formalGreeting") service: GreetingService): GreetingFacade =
        GreetingFacade(service)
}

class GreetingFacade(private val service: GreetingService) {
    fun greet(name: String): String = "[Facade] ${service.greet(name)}"
}

// ═══════════════════════════════════════════════════════════════
// @Value 属性注入 + @PropertySource 示例
// ═══════════════════════════════════════════════════════════════

/**
 * @Value 示例：从系统属性或配置文件注入值。
 * 支持 ${key:default} 格式的默认值。
 */
@Component
class AppSettings {
    @Value("\${app.name:TabooLib IoC Example}")
    var appName: String = ""

    @Value("\${app.version:1.0.0}")
    var version: String = ""

    @Value("\${app.debug:false}")
    var debug: Boolean = false
}

// ═══════════════════════════════════════════════════════════════
// @Lazy 延迟注入示例
// ═══════════════════════════════════════════════════════════════

interface HeavyResource {
    fun load(): String
}

@Component
class HeavyResourceImpl : HeavyResource {
    override fun load(): String = "Heavy resource loaded"
}

/**
 * @Lazy 示例：延迟注入，只在首次使用时才解析依赖。
 * 适用于启动时不需要立即加载的重量级资源。
 */
@Component
class LazyConsumer {
    @Inject
    @Lazy
    lateinit var resource: HeavyResource

    fun useResource(): String = resource.load()
}

// ═══════════════════════════════════════════════════════════════
// @Prototype 作用域示例
// ═══════════════════════════════════════════════════════════════

/**
 * @Prototype 示例：每次获取都创建新实例。
 */
@Prototype
@Component
class RequestContext {
    val id: String = java.util.UUID.randomUUID().toString().take(8)
}

// ═══════════════════════════════════════════════════════════════
// @Primary / @Order 示例
// ═══════════════════════════════════════════════════════════════

interface NotificationSender {
    fun send(message: String): String
}

@Primary
@Order(1)
@Component
class EmailSender : NotificationSender {
    override fun send(message: String): String = "Email: $message"
}

@Order(2)
@Component
class SmsSender : NotificationSender {
    override fun send(message: String): String = "SMS: $message"
}

/**
 * @Primary 示例：当有多个同类型 Bean 时，@Primary 标记的会被优先注入。
 * @Order 示例：控制 Bean 的排序优先级。
 */
@Component
class NotificationService {
    @Inject
    lateinit var sender: NotificationSender

    fun notify(message: String): String = sender.send(message)
}

// ═══════════════════════════════════════════════════════════════
// @ConditionalOnClass 条件装配示例
// ═══════════════════════════════════════════════════════════════

/**
 * @ConditionalOnClass 示例：只有当指定类存在时才注册。
 * 适用于可选依赖的场景。
 */
@ConditionalOnClass("org.bukkit.Bukkit")
@Component
class BukkitIntegration {
    fun isAvailable(): Boolean = true
}

// ═══════════════════════════════════════════════════════════════
// @DependsOn 显式初始化顺序示例
// ═══════════════════════════════════════════════════════════════

@Component
class DatabaseConnection {
    @PostConstruct
    fun connect() {
        // 模拟数据库连接
    }
}

/**
 * @DependsOn 示例：确保 DatabaseConnection 在 UserDao 之前初始化。
 */
@DependsOn("databaseConnection")
@Component
class UserDao {
    @Inject
    lateinit var db: DatabaseConnection
}

// ═══════════════════════════════════════════════════════════════
// BeanPostProcessor 扩展点示例
// ═══════════════════════════════════════════════════════════════

/**
 * BeanPostProcessor 示例：在 Bean 初始化前后执行自定义逻辑。
 * 可用于日志记录、性能监控、自动配置等。
 */
@Component
class LoggingPostProcessor : BeanPostProcessor {
    override fun postProcessAfterInitialization(bean: Any, beanName: String): Any {
        // 可以在这里添加日志、包装代理等
        return bean
    }
}

// ═══════════════════════════════════════════════════════════════
// EventBus 事件订阅示例
// ═══════════════════════════════════════════════════════════════

/**
 * EventBus 示例：监听容器事件。
 * BeanContainer.getEventBus() 可以订阅 BeanCreatedEvent、BeanDestroyedEvent 等。
 *
 * 用法：
 * ```kotlin
 * BeanContainer.getEventBus().subscribe(BeanCreatedEvent::class.java) { event ->
 *     info("Bean 创建: ${event.beanName}")
 * }
 * ```
 */

// ═══════════════════════════════════════════════════════════════
// @Inject(required=false) 可选注入示例
// ═══════════════════════════════════════════════════════════════

/**
 * @Inject(required=false) 示例：可选依赖，不存在时不抛异常。
 */
@Component
class OptionalFeature {
    @Inject(required = false)
    var analytics: AnalyticsService? = null

    fun isAnalyticsEnabled(): Boolean = analytics != null
}

interface AnalyticsService {
    fun track(event: String)
}

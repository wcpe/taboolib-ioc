# API 文档

本文档只描述当前版本真实可用的公开 API。

## 安装

### Gradle (Kotlin DSL)

使用 TabooLib 的 `taboo()` 方法将 IoC 容器打包到插件内：

```kotlin
repositories {
    maven("https://maven.wcpe.top/repository/maven-public/")
}

dependencies {
    taboo("top.wcpe.taboolib.ioc:taboolib-ioc:1.1.0-SNAPSHOT")
}

// 重定向到你的插件包名，避免与其他插件冲突
taboolib {
    relocate("top.wcpe.taboolib.ioc", "top.wcpe.yourplugin.ioc")
}
```

> **重要**：必须使用 `taboo()` 而非 `compileOnly()`，否则运行时找不到类。同时务必配置 `relocate` 重定向包名。


## 注解

### `@Component`

通用组件注解。

```kotlin
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class Component(val value: String = "")
```

- `value` 为空时，Bean 名称默认为类名首字母小写

### `@Service`

服务组件注解，行为与 `@Component` 一致。

```kotlin
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class Service(val value: String = "")
```

### `@Repository`

仓储组件注解，行为与 `@Component` 一致。

```kotlin
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class Repository(val value: String = "")
```

### `@Controller`

控制器组件注解，行为与 `@Component` 一致。

```kotlin
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class Controller(val value: String = "")
```

### `@Lazy`

延迟 Bean 初始化。

```kotlin
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class Lazy(val value: Boolean = true)
```

说明：

- 类级别：延迟 Bean 自身的创建时机，singleton Bean 会在首次解析时初始化，而不是在容器启动时预初始化
- 字段/参数级别：对接口类型的字段或构造函数参数使用 `@Lazy`，会创建 JDK 动态代理，首次调用方法时才解析真实 Bean
- 字段级别的 `@Lazy` 仅支持接口类型，非接口类型会回退到立即注入并输出警告

### `@Scope`

声明 Bean 作用域。

```kotlin
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class Scope(val value: String = "singleton")
```

说明：

- 默认作用域是 `singleton`
- 内置支持 `singleton` 与 `prototype`
- 其他名称会被当作自定义作用域，通过 `BeanContainer.registerScope(...)` 解析

### `@Primary`

标记首选 Bean。当同一类型存在多个 Bean 时，`getBean` 按类型解析优先返回标记了 `@Primary` 的。

```kotlin
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class Primary
```

示例：

```kotlin
import top.wcpe.yourplugin.ioc.annotation.Component
import top.wcpe.yourplugin.ioc.annotation.Primary

interface Cache {
    fun type(): String
}

@Component
@Primary
class RedisCache : Cache {
    override fun type() = "redis"
}

@Component
class LocalCache : Cache {
    override fun type() = "local"
}

// getBean(Cache::class.java) 返回 RedisCache
```

### `@Order`

控制 Bean 的排序优先级。

```kotlin
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class Order(val value: Int = Int.MAX_VALUE)
```

说明：

- 值越小优先级越高，默认为 `Int.MAX_VALUE`
- 影响 `getBeansOfType` 返回顺序
- 影响 AOP Advisor 执行顺序
- 无 `@Primary` 时，`getPrimaryByType` 返回 order 值最小的

示例：

```kotlin
import top.wcpe.yourplugin.ioc.annotation.Component
import top.wcpe.yourplugin.ioc.annotation.Order

@Component
@Order(1)
class HighPriorityHandler : Handler

@Component
@Order(100)
class LowPriorityHandler : Handler

// getBeansOfType(Handler::class.java) 返回 [HighPriority, LowPriority]
```

### `@Prototype`

`prototype` 作用域的快捷注解。

```kotlin
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class Prototype
```

说明：

- 每次 `getBean(...)` 或每次依赖解析都会创建新实例
- prototype Bean 不参与容器关闭时的统一 `@PreDestroy`

### `@ComponentScan`

限制当前插件 Jar 内的组件扫描范围。

```kotlin
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class ComponentScan(
    val value: Array<String> = [],
    val basePackages: Array<String> = [],
    val basePackageClasses: Array<KClass<*>> = []
)
```

说明：

- 未声明时，默认扫描当前插件 Jar 内的全部组件类
- 声明后，仅扫描指定包及其子包
- 未显式指定包时，默认使用声明该注解的类所在包

### `@Configuration`

标记一个类为配置类，其中的 `@Bean` 方法会被扫描并注册为 Bean 定义。

```kotlin
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class Configuration
```

说明：

- 配置类本身也会被注册为 singleton Bean
- 配置类中的 `@Bean` 方法在容器初始化时被调用，返回值作为 Bean 实例
- 可配合 `@PropertySource` 加载配置文件

### `@Bean`

在 `@Configuration` 类中声明一个 Bean。

```kotlin
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class Bean(val value: String = "")
```

说明：

- `value` 为空时，Bean 名称默认为方法名
- 方法参数会自动从容器中解析注入
- 参数支持 `@Named` 限定符和 `@Lazy` 延迟注入
- 返回类型上的 `@Inject`/`@Value` 字段会被自动注入
- 返回类型上的 `@PostConstruct`/`@PostEnable`/`@PreDestroy` 会被自动调用
- 如果返回类型是接口，实际实现类上的注入点和生命周期回调也会在运行时被发现
- 可配合 `@Primary`、`@Order`、`@Scope`、`@Lazy`、条件注解使用

示例：

```kotlin
import top.wcpe.yourplugin.ioc.annotation.*

@Configuration
class AppConfig {

    @Primary
    @Bean
    fun mainDataSource(): DataSource = MysqlDataSource("jdbc:mysql://localhost/db")

    @Bean("backupDataSource")
    fun backupDataSource(): DataSource = H2DataSource()

    // 参数自动注入，支持 @Named 限定
    @Bean
    fun userRepository(@Named("mainDataSource") ds: DataSource): UserRepository =
        UserRepository(ds)

    // 方法级别条件注解
    @ConditionalOnProperty(name = "cache.enabled", havingValue = "true")
    @Bean
    fun cacheService(): CacheService = RedisCacheService()
}
```

### `@PropertySource`

指定配置文件来源，标注在 `@Configuration` 类上。

```kotlin
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class PropertySource(vararg val value: String)
```

说明：

- `value`：classpath 相对路径列表
- 支持 `.properties` 格式和简单的 `.yml` 格式（仅扁平 `key: value`）
- 加载的属性可通过 `@Value` 注入
- 属性查找优先级：已加载配置文件 > 系统属性

示例：

```kotlin
@PropertySource("database.properties", "app.yml")
@Configuration
class AppConfig {
    @Bean
    fun settings(): AppSettings = AppSettings()
}

class AppSettings {
    @Value("\${db.url:jdbc:h2:mem:test}")
    var dbUrl: String = ""
}
```

### `@DependsOn`

声明当前 Bean 依赖于指定的 Bean，确保它们在当前 Bean 之前初始化。

```kotlin
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class DependsOn(vararg val value: String)
```

说明：

- `value`：依赖的 Bean 名称列表
- 容器会按拓扑排序确保依赖的 Bean 先初始化
- 可用于类级别和 `@Bean` 方法级别

示例：

```kotlin
@DependsOn("databaseConnection")
@Component
class UserDao {
    @Inject
    lateinit var db: DatabaseConnection
}
```

### `@Inject`

依赖注入注解。

```kotlin
@Target(AnnotationTarget.CONSTRUCTOR, AnnotationTarget.FIELD, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class Inject(val required: Boolean = true)
```

支持：

- 构造函数注入
- 字段注入
- 方法注入
- Kotlin `lateinit var` 属性注入

说明：

- `required = true`（默认）：注入失败时抛出 `IllegalStateException`
- `required = false`：注入失败时字段保持 null，输出 warning 日志

示例：

```kotlin
import top.wcpe.yourplugin.ioc.annotation.Service
import top.wcpe.yourplugin.ioc.annotation.Inject

@Service
class UserService @Inject constructor(
    private val repository: UserRepository
) {

    @Inject
    lateinit var formatter: TextFormatter

    private lateinit var logger: LoggerService

    @Inject
    fun bindLogger(logger: LoggerService) {
        this.logger = logger
    }
}
```

```kotlin
@Component
class PluginFeature {
    @Inject(required = false)
    var analytics: AnalyticsService? = null
}
```

### `@Named`

给 `@Inject` 指定名称限定。

```kotlin
@Target(AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
annotation class Named(val value: String = "")
```

示例：

```kotlin
import top.wcpe.yourplugin.ioc.annotation.Inject
import top.wcpe.yourplugin.ioc.annotation.Named

@Inject
@Named("wechatGateway")
lateinit var gateway: PaymentGateway
```

### `@Resource`

按名称注入资源。

```kotlin
@Target(AnnotationTarget.FIELD, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class Resource(val name: String = "")
```

说明：

- 字段上使用时，按 `name` 查找 Bean
- 方法上使用时，当前实现按单参数 setter 语义处理

示例：

```kotlin
import top.wcpe.yourplugin.ioc.annotation.Resource

@Resource(name = "alipayGateway")
fun bindGateway(gateway: PaymentGateway) {
    this.gateway = gateway
}
```

### `@PostConstruct`

Bean 完成依赖注入后执行。

```kotlin
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class PostConstruct
```

要求：

- 方法无参数
- 一个类可以有多个标注了该注解的方法，所有方法都会被调用

### `@PostEnable`

插件 ENABLE 后统一执行的回调。在所有 Bean 创建完毕、object 注入完成后调用。

```kotlin
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class PostEnable
```

要求：

- 方法无参数
- 一个类可以有多个标注了该注解的方法，所有方法都会被调用

说明：

- 执行时序：`@PostConstruct`（Bean 创建时）→ object 注入 → `@PostEnable`（ENABLE -80）→ 用户 `@Awake(LifeCycle.ENABLE)`
- 适用于需要在所有 Bean 就绪后才能执行的初始化逻辑（如跨 Bean 协调、注册监听器等）

示例：

```kotlin
import top.wcpe.yourplugin.ioc.annotation.Service
import top.wcpe.yourplugin.ioc.annotation.PostEnable
import top.wcpe.yourplugin.ioc.annotation.Inject

@Service
class GameManager {

    @Inject
    lateinit var playerService: PlayerService

    @Inject
    lateinit var worldService: WorldService

    @PostEnable
    fun onAllReady() {
        // 此时所有 Bean 已创建完毕，可以安全地进行跨 Bean 协调
        worldService.registerListener(playerService)
    }
}
```

### `@PreDestroy`

容器关闭时执行。

```kotlin
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class PreDestroy
```

要求：

- 方法无参数
- 一个类可以有多个标注了该注解的方法，所有方法都会被调用

### `@Aspect`

标记一个类为切面。切面类会自动注册为组件，无需额外标记 `@Component`。

```kotlin
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class Aspect
```

说明：

- 切面类中可以定义 `@Before`、`@After`、`@Around` 通知方法
- 切面 Bean 在容器初始化时优先创建，确保普通 Bean 创建时 AOP 代理已就绪
- 切面 Bean 自身不会被 AOP 代理

### `@Before`

前置通知，在目标方法执行之前调用。

```kotlin
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class Before(val value: String)
```

- `value`：切点表达式，格式为 `execution(类名.方法名)`
- 通知方法可以无参，也可以接收与目标方法相同的参数

示例：

```kotlin
@Aspect
class LogAspect {
    @Before("execution(OrderService.placeOrder)")
    fun beforeOrder() {
        println("准备下单")
    }
}
```

### `@After`

后置通知，在目标方法执行之后调用（无论是否抛出异常）。

```kotlin
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class After(val value: String)
```

- `value`：切点表达式
- 即使目标方法抛出异常，`@After` 通知仍会执行

### `@Around`

环绕通知，包裹目标方法的执行。

```kotlin
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class Around(val value: String)
```

- `value`：切点表达式
- 通知方法必须接收一个 `MethodInvocation` 参数
- 必须调用 `invocation.proceed()` 继续执行，否则目标方法不会被调用（短路）
- 可以修改返回值

示例：

```kotlin
@Aspect
class TimingAspect {
    @Around("execution(OrderService.placeOrder)")
    fun timing(invocation: MethodInvocation): Any? {
        val start = System.currentTimeMillis()
        val result = invocation.proceed()
        println("耗时: ${System.currentTimeMillis() - start}ms")
        return result
    }
}
```

### `@Pointcut`

定义可复用的切点表达式，其他通知注解可以通过方法名引用。

```kotlin
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class Pointcut(val value: String)
```

示例：

```kotlin
@Aspect
class MyAspect {
    @Pointcut("execution(OrderService.*)")
    fun orderMethods() {}

    @Before("orderMethods")  // 通过方法名引用切点
    fun beforeOrder() { ... }
}
```

### 切点表达式语法

| 格式 | 说明 |
|------|------|
| `execution(类名.方法名)` | 精确匹配（支持简单类名和全限定名） |
| `execution(*.方法名)` | 匹配所有类的指定方法 |
| `execution(包名..*.方法名)` | 匹配包及子包下所有类的指定方法 |
| `execution(类名.*)` | 匹配类的所有方法 |

也可以省略 `execution()` 包裹，直接写 `类名.方法名`。

### `MethodInvocation`

`@Around` 通知的方法调用上下文。

```kotlin
class MethodInvocation(
    val target: Any,           // 目标对象
    val method: Method,        // 被调用的方法
    val arguments: Array<out Any?>?  // 方法参数
) {
    fun proceed(): Any?        // 继续执行拦截器链或目标方法
}
```

### `@Conditional`

通用条件注解，指定一个或多个 `Condition` 实现类。

```kotlin
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class Conditional(vararg val value: KClass<out Condition>)
```

- 多个 Condition 之间为 AND 关系，全部满足才注册
- `Condition` 接口需实现 `fun matches(context: ConditionContext): Boolean`
- 可用于类级别和 `@Bean` 方法级别

### `@ConditionalOnClass`

当指定的类存在于 ClassPath 中时注册。

```kotlin
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class ConditionalOnClass(vararg val value: String)
```

- `value`：类的全限定名
- 多个类名之间为 AND 关系
- 可用于类级别和 `@Bean` 方法级别

### `@ConditionalOnMissingClass`

当指定的类不存在于 ClassPath 中时注册。

```kotlin
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class ConditionalOnMissingClass(vararg val value: String)
```

- 可用于类级别和 `@Bean` 方法级别

### `@ConditionalOnBean`

当容器中存在指定类型或名称的 Bean 时注册。

```kotlin
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class ConditionalOnBean(
    vararg val value: KClass<*> = [],
    val name: Array<String> = []
)
```

- `value` 和 `name` 之间为 AND 关系
- 在阶段二（所有非条件 Bean 注册后）评估
- 可用于类级别和 `@Bean` 方法级别

### `@ConditionalOnMissingBean`

当容器中不存在指定类型或名称的 Bean 时注册。

```kotlin
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class ConditionalOnMissingBean(
    vararg val value: KClass<*> = [],
    val name: Array<String> = []
)
```

- 可用于类级别和 `@Bean` 方法级别

### `@ConditionalOnProperty`

当系统属性匹配指定值时注册。

```kotlin
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class ConditionalOnProperty(
    val name: String,
    val havingValue: String = "",
    val matchIfMissing: Boolean = false
)
```

- `havingValue` 为空时，仅检查属性是否存在
- `matchIfMissing`：属性不存在时是否视为匹配
- 可用于类级别和 `@Bean` 方法级别

### `Condition` 接口

自定义条件实现接口。

```kotlin
interface Condition {
    fun matches(context: ConditionContext): Boolean
}
```

### `ConditionContext` 接口

条件评估上下文，提供容器和类加载器信息。

```kotlin
interface ConditionContext {
    fun getClassLoader(): ClassLoader
    fun containsBeanDefinition(name: String): Boolean
    fun getBeanNamesForType(type: Class<*>): List<String>
}
```

### `@ThreadScope`

线程作用域快捷注解，等价于 `@Scope("thread")`。

```kotlin
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class ThreadScope
```

- 每个线程持有独立的 Bean 实例
- 通过 `BeanContainer.getThreadScope()?.clearCurrentThread()` 清理当前线程缓存

### `@RefreshScope`

可刷新作用域快捷注解，等价于 `@Scope("refresh")`。

```kotlin
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class RefreshScope
```

- Bean 实例会被缓存，通过 `BeanContainer.refreshScope()` 触发重建
- 支持按名称刷新：`BeanContainer.refreshScope("beanName")`

## 循环依赖

- singleton Bean 的字段注入和方法注入循环依赖会在早期暴露阶段完成
- 构造函数循环依赖会在 `ACTIVE` 初始化阶段抛出 `CircularDependencyException`
- 异常会携带完整依赖链，便于快速定位问题

## Kotlin 扩展方法

提供更简洁的 Bean 获取方式，基于 Kotlin reified 泛型。

### `bean<T>(name?)`

```kotlin
inline fun <reified T> bean(name: String? = null): T
```

行为：

- 按类型获取 Bean，找不到时抛出 `IllegalStateException`
- `name` 不为空时按名称限定

示例：

```kotlin
import top.wcpe.yourplugin.ioc.bean.bean

val service = bean<UserService>()
val gateway = bean<PaymentGateway>("wechatGateway")
```

### `beanOrNull<T>(name?)`

```kotlin
inline fun <reified T> beanOrNull(name: String? = null): T?
```

行为：

- 按类型获取 Bean，找不到时返回 `null`

### `beans<T>()`

```kotlin
inline fun <reified T> beans(): List<T>
```

行为：

- 获取指定类型的所有 Bean（包括手动注册的）

示例：

```kotlin
import top.wcpe.yourplugin.ioc.bean.beans

val gateways = beans<PaymentGateway>()
```

## `BeanPostProcessor`

Bean 后处理器扩展点，允许在 Bean 初始化前后对实例进行自定义处理。

```kotlin
interface BeanPostProcessor {
    fun postProcessBeforeInitialization(bean: Any, beanName: String): Any = bean
    fun postProcessAfterInitialization(bean: Any, beanName: String): Any = bean
}
```

说明：

- 实现此接口的 Bean 会被自动发现并注册
- `postProcessBeforeInitialization`：在 `@PostConstruct` 之前调用
- `postProcessAfterInitialization`：在 `@PostConstruct` 之后、AOP 代理之前调用
- 可以返回原始实例或包装后的代理

示例：

```kotlin
@Component
class LoggingPostProcessor : BeanPostProcessor {
    override fun postProcessAfterInitialization(bean: Any, beanName: String): Any {
        println("Bean 初始化完成: $beanName (${bean.javaClass.simpleName})")
        return bean
    }
}
```

## `BeanContainer`

### `getBean(type, name?)`

```kotlin
fun <T> getBean(type: Class<T>, name: String? = null): T?
```

行为：

- `name != null` 时，优先按名称解析
- `name == null` 时，优先从容器组件中按类型解析
- 如果没有托管组件，再回退到手动注册的同类型实例
- 支持接口和父类类型查询

示例：

```kotlin
import top.wcpe.yourplugin.ioc.bean.BeanContainer

val service = BeanContainer.getBean(UserService::class.java)
val gateway = BeanContainer.getBean(PaymentGateway::class.java, "wechatGateway")
```

### `getBeansOfType(type)`

```kotlin
fun <T> getBeansOfType(type: Class<T>): List<T>
```

行为：

- 返回所有可赋值到该类型的容器组件
- 同时包含 `registerBean` 手动注册的同类型实例

示例：

```kotlin
import top.wcpe.yourplugin.ioc.bean.BeanContainer

val gateways = BeanContainer.getBeansOfType(PaymentGateway::class.java)
```

### `containsBean(name)`

```kotlin
fun containsBean(name: String): Boolean
```

行为：

- 同时检查容器组件和手动注册实例

### `getBeanNames()`

```kotlin
fun getBeanNames(): Set<String>
```

行为：

- 返回当前容器组件名称和手动注册名称的并集

### `registerBean(name, instance)`

```kotlin
fun registerBean(name: String, instance: Any)
```

行为：

- 手动注册一个现成单例
- 注册后可以被 `containsBean`、`getBeanNames`、`getBeansOfType` 看到
- 可以通过 `getBean(type, name)` 直接按名称获取

示例：

```kotlin
import top.wcpe.yourplugin.ioc.bean.BeanContainer

BeanContainer.registerBean("manualValue", ManualValue("ok"))
val value = BeanContainer.getBean(ManualValue::class.java, "manualValue")
```

### `registerScope(name, scope)`

```kotlin
fun registerScope(name: String, scope: BeanScope)
```

行为：

- 注册一个自定义 Bean 作用域
- `name` 不能覆盖内置的 `singleton` / `prototype`
- 需在容器初始化前完成注册，供 `@Scope("...")` Bean 使用

示例：

```kotlin
import top.wcpe.yourplugin.ioc.bean.BeanContainer
import top.wcpe.yourplugin.ioc.bean.BeanScope
import top.wcpe.yourplugin.ioc.bean.BeanDefinition
import java.util.concurrent.ConcurrentHashMap

BeanContainer.registerScope("conversation", object : BeanScope {
    private val cache = ConcurrentHashMap<String, Any>()

    override fun get(name: String, definition: BeanDefinition, creator: () -> Any): Any {
        return cache.getOrPut(name, creator)
    }
})
```

### `refreshScope(name?)`

```kotlin
fun refreshScope(name: String? = null)
```

行为：

- 刷新 `refresh` 作用域中的 Bean 缓存
- `name` 不为空时，仅刷新指定 Bean；为空时刷新全部
- 下次获取时会重新创建实例

示例：

```kotlin
// 刷新所有 refresh 作用域的 Bean
BeanContainer.refreshScope()

// 刷新指定 Bean
BeanContainer.refreshScope("dynamicConfig")
```

### `getThreadScope()`

```kotlin
fun getThreadScope(): ThreadBeanScope?
```

行为：

- 获取内置的线程作用域实例
- 可用于手动清理当前线程的 Bean 缓存

示例：

```kotlin
// 清理当前线程的所有 ThreadScope Bean 缓存
BeanContainer.getThreadScope()?.clearCurrentThread()
```
## 扫描与生命周期

### 扫描时机

- 组件类在 TabooLib `LOAD` 阶段由 `ComponentVisitor` 完成扫描
- 扫描源来自当前插件 Jar 的类表
- 不依赖你的业务包名是否包含 `.taboolib.`
- 如果存在 `@ComponentScan`，只会注册命中包范围的组件

### 容器初始化时机

- `ENABLE` 前置任务（优先级 -100）：初始化容器，创建 eager singleton，执行 `@PostConstruct`
- `ENABLE` 前置任务（优先级 -90）：`ObjectInjector` 注入 Kotlin `object` 字段
- `ENABLE` 前置任务（优先级 -80）：执行所有 singleton Bean 的 `@PostEnable` 回调
- `DISABLE` 收尾任务（优先级 100）：关闭容器，按逆序调用 `@PreDestroy`
- `@Lazy` singleton、`prototype` 与自定义作用域 Bean 会在首次解析时按需创建
- 依赖插件在 `@Awake(LifeCycle.ENABLE)` 或 `onEnable()` 中即可使用 IoC Bean
- `@PropertySource` 配置文件在 LOAD 阶段扫描 `@Configuration` 类时加载
- `BeanPostProcessor` 在容器初始化时优先创建并注册
- `@DependsOn` 在初始化 eager singleton 时按拓扑排序处理

## 构造函数解析规则

容器按以下顺序选择构造函数：

1. 显式标了 `@Inject` 的构造函数
2. 类唯一的构造函数
3. 无参构造函数

如果一个类存在多个构造函数并且没有 `@Inject`，容器会抛出异常，你需要显式标记。

## Bean 命名规则

- 注解 `value` 非空时，使用它
- 否则使用类名首字母小写

示例：

```kotlin
import top.wcpe.yourplugin.ioc.annotation.Service
import top.wcpe.yourplugin.ioc.annotation.Repository

@Service("customService")
class MyService

@Repository
class UserRepository // bean name: userRepository
```

## Kotlin `object` 自动注入

所有位于插件 Jar 中、字段上带 `@Inject` 或 `@Resource` 的 Kotlin `object` 都会自动注入。

示例：

```kotlin
import top.wcpe.yourplugin.ioc.annotation.Inject

object PluginState {

    @Inject
    lateinit var userService: UserService
}
```

## 示例插件覆盖

`taboolib-ioc-example` 当前覆盖：

- `@Controller` 构造函数注入
- `@Service` 字段注入
- `@Inject` 方法注入
- `@Resource(name = ...)` 方法注入
- `@Named` 名称限定
- 字段循环依赖示例
- 构造函数循环依赖检测示例
- `@PostConstruct` / `@PreDestroy`
- Kotlin `object` 自动注入
- `getBean` / `getBeansOfType` / `containsBean` / `getBeanNames` / `registerBean`

## 作用域与懒加载说明

- singleton Bean 支持字段/方法注入形成的循环依赖早期暴露
- prototype / 自定义作用域 Bean 采用按需创建，不提供单例式的循环依赖缓存
- 自定义作用域实例如何缓存、何时失效，由 `BeanScope` 实现自行决定
- `@ThreadScope` 和 `@RefreshScope` 是内置作用域，容器初始化时自动注册，无需手动调用 `registerScope`

## AOP 说明

- AOP 基于 JDK 动态代理实现，目标 Bean 必须实现接口才能被代理
- 没有实现接口的 Bean 即使有匹配的 Advisor 也不会被代理
- 没有实现接口的 Bean 如果有匹配的 Advisor，会输出 warning 日志提示
- 切面 Bean 在容器初始化时优先创建，确保普通 Bean 创建时 AOP 代理已就绪
- 切面 Bean 自身不会被 AOP 代理
- `@After` 通知在目标方法抛出异常时仍会执行
- `@Around` 通知不调用 `proceed()` 时，目标方法不会被执行（短路）
- 多个 `@Around` 通知会形成拦截器链，按注册顺序依次执行

## 条件装配说明

条件评估分两阶段：

1. 扫描时（阶段一）：评估 `@Conditional`、`@ConditionalOnClass`、`@ConditionalOnMissingClass`、`@ConditionalOnProperty`
2. 注册后（阶段二）：评估 `@ConditionalOnBean`、`@ConditionalOnMissingBean`

同一个类上可以叠加多个条件注解，所有条件之间为 AND 关系。




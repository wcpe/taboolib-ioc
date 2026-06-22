# Taboolib IoC

为 TabooLib Bukkit 插件场景提供的轻量 IoC 容器。

[![版本](https://img.shields.io/badge/版本-1.1.0-blue)](CHANGELOG.md)
[![Kotlin](https://img.shields.io/badge/Kotlin-1.9.25-orange)](https://kotlinlang.org)
[![TabooLib](https://img.shields.io/badge/TabooLib-6.2.4-green)](https://tabooproject.org)

## 当前支持

- 组件标记：`@Component`、`@Service`、`@Repository`、`@Controller`
- 依赖注入：构造函数、字段、方法注入
- 属性注入：`@Value("${property:default}")` 从系统属性注入值
- 容器初始化：非 lazy singleton 在 `ENABLE` 阶段预初始化，其他作用域按需创建
- 名称限定：`@Named`、`@Resource`、`@Primary`
- 生命周期：`@PostConstruct`、`@PostEnable`、`@PreDestroy`
- 作用域：默认 singleton、`@Prototype`、`@Scope`、`@ThreadScope`、`@RefreshScope` 与 `registerScope` 自定义作用域
- 扫描控制：`@ComponentScan`
- 懒加载：`@Lazy`（类级别延迟初始化 + 字段/参数级别代理懒加载）
- 排序控制：`@Order` 控制 `getBeansOfType` 返回顺序和 AOP Advisor 执行顺序
- 事件机制：`EventBus` 监听 Bean 创建/销毁和容器生命周期事件
- 循环依赖检测：singleton Bean 的字段/方法循环依赖可解析，构造函数循环依赖会输出依赖链
- Kotlin `object` / `companion object` 自动注入
- 容器查询：`getBean`、`getBeansOfType`、`containsBean`、`getBeanNames`
- 手动注册单例：`registerBean`
- 按接口和父类类型解析 Bean
- AOP 支持：`@Aspect`、`@Before`、`@After`、`@Around`、`@Pointcut`，基于 JDK 动态代理
- 条件装配：`@Conditional`、`@ConditionalOnClass`、`@ConditionalOnMissingClass`、`@ConditionalOnBean`、`@ConditionalOnMissingBean`、`@ConditionalOnProperty`
- Kotlin 扩展方法：`bean<T>()`、`beanOrNull<T>()`、`beans<T>()`
- Java Config：`@Configuration` + `@Bean` 方法声明 Bean，支持 `@Named` 参数限定、`@Lazy` 参数、`@Primary`、`@Order`、`@Scope`
- `@Bean` 产物增强：支持 `@PostConstruct`/`@PostEnable`/`@PreDestroy` 生命周期回调、`@Value`/`@Inject` 字段注入
- `@Bean` 方法级别条件注解：`@ConditionalOnClass`/`@ConditionalOnProperty` 等可直接标注在 `@Bean` 方法上
- `@PropertySource`：在 `@Configuration` 类上指定配置文件，支持 `.properties` 和简单 `.yml` 格式
- `@DependsOn`：显式声明 Bean 初始化顺序依赖
- `@Inject(required = false)`：可选注入，依赖不存在时不抛异常
- `BeanPostProcessor`：Bean 初始化前后的扩展回调
- 多生命周期方法：同一个类可以有多个 `@PostConstruct`/`@PostEnable`/`@PreDestroy` 方法

## 作用域与扫描说明

当前版本已经重新提供并实现以下能力：

- `@Lazy`：仅延迟 Bean 自身的创建，首次被解析时初始化
- `@ComponentScan`：可按包名或基准类限制当前插件 Jar 内的组件扫描范围
- `@Prototype`：每次解析都会创建新实例
- `@ThreadScope`：线程级作用域，每个线程持有独立的 Bean 实例
- `@RefreshScope`：可刷新作用域，支持运行时通过 `BeanContainer.refreshScope()` 触发重建
- `@Scope("custom")`：配合 `BeanContainer.registerScope(...)` 使用自定义作用域

说明：

- 默认仍是 singleton 单例作用域
- singleton Bean 支持字段/方法循环依赖的早期暴露
- prototype / 自定义作用域 Bean 采用按需创建，不参与容器关闭时的统一 `@PreDestroy`
- `@ThreadScope` 和 `@RefreshScope` 是内置作用域，无需手动注册

## 安装

### Gradle (Kotlin DSL)

使用 TabooLib 的 `taboo()` 方法将 IoC 容器打包到插件内：

```kotlin
repositories {
    maven("https://maven.wcpe.top/repository/maven-public/")
}

dependencies {
    taboo("top.wcpe.taboolib.ioc:taboolib-ioc:1.1.0")
}

// 重定向到你的插件包名，避免与其他插件冲突
taboolib {
    relocate("top.wcpe.taboolib.ioc", "top.wcpe.yourplugin.ioc")
}
```

> **重要**：必须使用 `taboo()` 而非 `compileOnly()`，否则运行时找不到类。同时务必配置 `relocate` 重定向包名。

## 快速开始

### 1. 定义组件

```kotlin
import top.wcpe.yourplugin.ioc.annotation.Repository
import top.wcpe.yourplugin.ioc.annotation.Service
import top.wcpe.yourplugin.ioc.annotation.Component
import top.wcpe.yourplugin.ioc.annotation.Inject

// 仓储层 - 使用 @Repository 标记
@Repository
class UserRepository {
    fun findUserById(id: String): String = "User($id)"
}

// 服务层 - 使用 @Service 标记，构造函数注入
@Service
class UserService @Inject constructor(
    private val repository: UserRepository
) {
    fun getUser(id: String): String = repository.findUserById(id)
}

// 通用组件 - 使用 @Component 标记
@Component
class TextFormatter {
    fun format(label: String, value: Any): String = "$label=$value"
}
```

### 2. 使用依赖注入

```kotlin
import top.wcpe.yourplugin.ioc.annotation.Service
import top.wcpe.yourplugin.ioc.annotation.Inject

@Service
class OrderService {

    // 字段注入
    @Inject
    lateinit var userService: UserService

    // 方法注入
    @Inject
    fun bindFormatter(formatter: TextFormatter) {
        this.formatter = formatter
    }

    private lateinit var formatter: TextFormatter

    fun processOrder(userId: String): String {
        val user = userService.getUser(userId)
        return formatter.format("order", user)
    }
}
```

### 3. 名称限定注入

当同一接口有多个实现时，使用 `@Named` 或 `@Resource` 指定具体实现：

```kotlin
import top.wcpe.yourplugin.ioc.annotation.Component
import top.wcpe.yourplugin.ioc.annotation.Service
import top.wcpe.yourplugin.ioc.annotation.Inject
import top.wcpe.yourplugin.ioc.annotation.Named
import top.wcpe.yourplugin.ioc.annotation.Resource

interface PaymentGateway {
    fun channel(): String
}

@Component("wechatGateway")
class WechatGateway : PaymentGateway {
    override fun channel() = "wechat"
}

@Component("alipayGateway")
class AlipayGateway : PaymentGateway {
    override fun channel() = "alipay"
}

@Service
class PaymentService {

    // 使用 @Named 指定注入 wechatGateway
    @Inject
    @Named("wechatGateway")
    lateinit var primaryGateway: PaymentGateway

    // 使用 @Resource 指定注入 alipayGateway
    @Resource(name = "alipayGateway")
    fun bindFallback(gateway: PaymentGateway) {
        this.fallbackGateway = gateway
    }

    private lateinit var fallbackGateway: PaymentGateway
}
```

### 4. 生命周期回调

```kotlin
import top.wcpe.yourplugin.ioc.annotation.Service
import top.wcpe.yourplugin.ioc.annotation.PostConstruct
import top.wcpe.yourplugin.ioc.annotation.PostEnable
import top.wcpe.yourplugin.ioc.annotation.PreDestroy

@Service
class LifecycleService {

    @PostConstruct
    fun onInit() {
        println("Bean 初始化完成，依赖注入已执行")
    }

    @PostEnable
    fun onEnable() {
        println("所有 Bean 已就绪，插件 ENABLE 阶段统一执行")
    }

    @PreDestroy
    fun onDestroy() {
        println("容器关闭前执行清理")
    }
}
```

### 5. 从容器获取 Bean

```kotlin
import top.wcpe.yourplugin.ioc.bean.BeanContainer

// 按类型获取
val userService = BeanContainer.getBean(UserService::class.java)

// 按名称获取
val gateway = BeanContainer.getBean(PaymentGateway::class.java, "wechatGateway")

// 获取某类型的所有 Bean
val allGateways = BeanContainer.getBeansOfType(PaymentGateway::class.java)

// 检查 Bean 是否存在
val exists = BeanContainer.containsBean("userService")

// 获取所有 Bean 名称
val names = BeanContainer.getBeanNames()

// 手动注册 Bean
BeanContainer.registerBean("manualValue", MyCustomObject("data"))
```

#### Kotlin 扩展方法

更简洁的 Bean 获取方式：

```kotlin
import top.wcpe.yourplugin.ioc.bean.bean
import top.wcpe.yourplugin.ioc.bean.beanOrNull
import top.wcpe.yourplugin.ioc.bean.beans

// 按类型获取，找不到抛异常
val userService = bean<UserService>()

// 按名称获取
val gateway = bean<PaymentGateway>("wechatGateway")

// 按类型获取，找不到返回 null
val optional = beanOrNull<UserService>()

// 获取某类型的所有 Bean
val allGateways = beans<PaymentGateway>()
```

### 6. Kotlin object / companion object 注入

```kotlin
import top.wcpe.yourplugin.ioc.annotation.Inject
import top.wcpe.yourplugin.ioc.annotation.Named

// Kotlin object 自动注入
object PluginState {

    @Inject
    lateinit var userService: UserService

    fun doSomething() {
        userService.getUser("123")
    }
}

// companion object 自动注入（非 @JvmField，推荐写法）
class MyPlugin {
    companion object {
        @Inject
        lateinit var userService: UserService

        @Inject
        @Named("wechatGateway")
        lateinit var gateway: PaymentGateway
    }
}

// companion object 注入（@JvmField 写法）
class AnotherPlugin {
    companion object {
        @Inject
        @JvmField
        var userService: UserService? = null
    }
}
```

> 说明：`object` 和 `companion object` 中带 `@Inject`/`@Resource` 的字段均在 ENABLE -90 阶段自动注入，无需手动操作。

### 7. 作用域与懒加载

```kotlin
import top.wcpe.yourplugin.ioc.annotation.Service
import top.wcpe.yourplugin.ioc.annotation.Prototype
import top.wcpe.yourplugin.ioc.annotation.Lazy
import top.wcpe.yourplugin.ioc.annotation.Scope

// 默认单例
@Service
class SingletonService

// 每次获取都创建新实例
@Service
@Prototype
class PrototypeService

// 延迟初始化，首次使用时才创建
@Service
@Lazy
class LazyService

// 自定义作用域
@Service
@Scope("conversation")
class ConversationService
```

### 8. AOP 切面编程

使用 `@Aspect` 定义切面，通过 `@Before`、`@After`、`@Around` 拦截方法调用：

```kotlin
import top.wcpe.yourplugin.ioc.annotation.*
import top.wcpe.yourplugin.ioc.bean.MethodInvocation

interface OrderService {
    fun placeOrder(orderId: String): String
}

@Service
class OrderServiceImpl : OrderService {
    override fun placeOrder(orderId: String): String {
        println("下单: $orderId")
        return "OK"
    }
}

@Aspect
class LoggingAspect {

    @Before("execution(OrderServiceImpl.placeOrder)")
    fun beforeOrder() {
        println("准备下单...")
    }

    @After("execution(OrderServiceImpl.placeOrder)")
    fun afterOrder() {
        println("下单完成")
    }

    @Around("execution(OrderServiceImpl.placeOrder)")
    fun aroundOrder(invocation: MethodInvocation): Any? {
        val start = System.currentTimeMillis()
        val result = invocation.proceed()
        println("耗时: ${System.currentTimeMillis() - start}ms")
        return result
    }
}
```

切点表达式支持：
- `execution(类名.方法名)` — 精确匹配
- `execution(*.方法名)` — 匹配所有类的指定方法
- `execution(包名..*.方法名)` — 匹配包下所有类的指定方法
- `execution(类名.*)` — 匹配类的所有方法

> 注意：AOP 代理基于 JDK 动态代理，目标 Bean 必须实现接口才能被代理。`@Aspect` 类会自动注册为组件，无需额外标记 `@Component`。

### 9. 条件装配

根据运行时条件决定是否注册 Bean：

```kotlin
import top.wcpe.yourplugin.ioc.annotation.*

// 仅当 ClassPath 中存在 Redis 客户端时注册
@Service
@ConditionalOnClass("redis.clients.jedis.Jedis")
class RedisCache : Cache {
    override fun get(key: String): String? = TODO()
}

// 当没有其他 Cache 实现时，使用内存缓存作为兜底
@Service
@ConditionalOnMissingBean(Cache::class)
class InMemoryCache : Cache {
    override fun get(key: String): String? = TODO()
}

// 当系统属性 feature.audit=true 时启用审计
@Service
@ConditionalOnProperty(name = "feature.audit", havingValue = "true")
class AuditService

// 自定义条件
class ProductionCondition : Condition {
    override fun matches(context: ConditionContext): Boolean {
        return System.getProperty("env") == "production"
    }
}

@Service
@Conditional(ProductionCondition::class)
class ProductionOnlyService
```

条件评估分两阶段：
1. 扫描时：`@ConditionalOnClass`、`@ConditionalOnMissingClass`、`@ConditionalOnProperty`、`@Conditional`
2. 注册后：`@ConditionalOnBean`、`@ConditionalOnMissingBean`（依赖已注册的 Bean 信息）

### 10. 线程作用域与可刷新作用域

```kotlin
import top.wcpe.yourplugin.ioc.annotation.*
import top.wcpe.yourplugin.ioc.bean.BeanContainer

// 每个线程持有独立实例
@Service
@ThreadScope
class RequestContext {
    var userId: String = ""
}

// 可刷新作用域，支持运行时重建
@Service
@RefreshScope
class DynamicConfig {
    var maxRetries: Int = 3
}

// 使用
fun example() {
    // 刷新所有 refresh 作用域的 Bean
    BeanContainer.refreshScope()

    // 刷新指定 Bean
    BeanContainer.refreshScope("dynamicConfig")

    // 清理当前线程的 ThreadScope 缓存
    BeanContainer.getThreadScope()?.clearCurrentThread()
}
```

### 11. @Configuration + @Bean

```kotlin
import top.wcpe.yourplugin.ioc.annotation.*

interface DataSource {
    fun url(): String
}

class MysqlDataSource(private val jdbcUrl: String) : DataSource {
    override fun url(): String = jdbcUrl
}

@Configuration
class DatabaseConfig {

    @Bean
    fun dataSource(@Named("jdbcUrl") url: String): DataSource = MysqlDataSource(url)

    @Primary
    @Bean("mainCache")
    fun mainCache(): CacheService = RedisCacheService()

    @ConditionalOnProperty(name = "cache.local.enabled", havingValue = "true")
    @Bean
    fun localCache(): CacheService = LocalCacheService()
}
```

### 12. @PropertySource 配置文件

```kotlin
import top.wcpe.yourplugin.ioc.annotation.*

// app.properties:
// app.name=MyPlugin
// app.version=2.0

@PropertySource("app.properties")
@Configuration
class AppConfig {
    @Bean
    fun appInfo(): AppInfo = AppInfo()
}

class AppInfo {
    @Value("\${app.name:DefaultApp}")
    var name: String = ""

    @Value("\${app.version:1.0}")
    var version: String = ""
}
```

### 13. BeanPostProcessor 扩展

```kotlin
import top.wcpe.yourplugin.ioc.annotation.Component
import top.wcpe.yourplugin.ioc.bean.BeanPostProcessor

@Component
class AuditPostProcessor : BeanPostProcessor {
    override fun postProcessAfterInitialization(bean: Any, beanName: String): Any {
        println("Bean 初始化完成: $beanName")
        return bean
    }
}
```

### 14. @DependsOn 初始化顺序

```kotlin
import top.wcpe.yourplugin.ioc.annotation.*

@Component
class DatabaseConnection {
    @PostConstruct
    fun connect() { println("数据库已连接") }
}

@DependsOn("databaseConnection")
@Component
class UserDao {
    @Inject
    lateinit var db: DatabaseConnection
}
```

### 15. @Inject(required = false) 可选注入

```kotlin
import top.wcpe.yourplugin.ioc.annotation.*

@Component
class PluginFeature {
    // 如果 AnalyticsService 没有注册，字段保持 null，不抛异常
    @Inject(required = false)
    var analytics: AnalyticsService? = null

    fun isAnalyticsEnabled(): Boolean = analytics != null
}
```

## 完整示例

以下是一个完整的插件示例，展示所有核心功能：

```kotlin
import taboolib.common.LifeCycle
import taboolib.common.platform.Awake
import top.wcpe.yourplugin.ioc.annotation.*
import top.wcpe.yourplugin.ioc.bean.BeanContainer

// 1. 定义仓储
@Repository
class UserRepository {
    fun loadStatus(): String = "ioc-ready"
}

// 2. 定义服务，使用构造函数注入
@Service
class ReportService @Inject constructor(
    private val repository: UserRepository
) {
    @Inject
    @Named("wechatGateway")
    lateinit var auditGateway: PaymentGateway

    @Resource(name = "alipayGateway")
    fun bindFallback(gateway: PaymentGateway) {
        this.fallbackGateway = gateway
    }

    private lateinit var fallbackGateway: PaymentGateway

    @PostConstruct
    fun onInit() {
        println("ReportService 初始化完成")
    }

    @PreDestroy
    fun onDestroy() {
        println("ReportService 销毁")
    }
}

// 3. 定义控制器
@Controller
class FeatureController @Inject constructor(
    private val reportService: ReportService
) {
    fun run() {
        // 从容器获取 Bean
        val service = BeanContainer.getBean(ReportService::class.java)
        val gateways = BeanContainer.getBeansOfType(PaymentGateway::class.java)
        println("Gateways: ${gateways.map { it.channel() }}")
    }
}

// 4. 插件入口
object ExamplePlugin {

    @Inject
    lateinit var controller: FeatureController

    @Awake(LifeCycle.ACTIVE)
    fun onActive() {
        // 手动注册 Bean
        BeanContainer.registerBean("customToken", CustomToken("value"))
        // 执行业务逻辑
        controller.run()
    }
}
```

## 容器 API

```kotlin
import top.wcpe.yourplugin.ioc.bean.BeanContainer

val userService = BeanContainer.getBean(UserService::class.java)
val namedService = BeanContainer.getBean(UserService::class.java, "userService")
val services = BeanContainer.getBeansOfType(UserService::class.java)
val exists = BeanContainer.containsBean("userService")
val names = BeanContainer.getBeanNames()

BeanContainer.registerBean("manualValue", ManualValue("ok"))
```

## 构造函数选择规则

- 优先使用带 `@Inject` 的构造函数
- 如果类只有一个构造函数，直接使用它
- 否则回退到无参构造函数
- 如果类存在多个构造函数且没有 `@Inject`，请显式标记一个构造函数

## 示例插件

示例插件已经覆盖全部公开能力，并在 `ACTIVE` 启动时统一输出验证结果。

覆盖内容：

- `@Controller` 构造函数注入
- `@Service` 字段注入
- `@Resource` 方法注入
- `@Named` 名称限定注入
- 字段循环依赖示例
- 构造函数循环依赖检测示例
- `@PostConstruct` / `@PostEnable` / `@PreDestroy`
- Kotlin `object` / `companion object` 自动注入
- `BeanContainer` 全部公开查询/注册方法
- Kotlin 扩展方法 `bean<T>()`、`beanOrNull<T>()`、`beans<T>()`
- 接口类型 `getBeansOfType` 聚合查询
- `@Configuration` + `@Bean` Java Config
- `@PropertySource` 配置文件加载
- `@DependsOn` 初始化顺序
- `@Inject(required = false)` 可选注入
- `BeanPostProcessor` 扩展点
- `@Bean` 方法级别条件注解

核心入口见：

- `taboolib-ioc-example/src/main/kotlin/top/wcpe/ioc/example/ExamplePlugin.kt`
- `taboolib-ioc-example/src/main/kotlin/top/wcpe/ioc/example/controller/ExampleFeatureController.kt`
- `taboolib-ioc-example/src/main/kotlin/top/wcpe/ioc/example/service/ExampleReportService.kt`
- `taboolib-ioc-example/src/main/kotlin/top/wcpe/ioc/example/support/ExampleCycleShowcase.kt`

预期启动日志包含：

```text
Taboolib IoC Example Plugin 启动
constructorInjection=ioc-ready
fieldNamedInjection=wechat
methodResourceInjection=alipay
methodInject=ExampleTextComponent
postConstruct=true
postEnable=true
getBeanByType=ExampleReportService
getBeanByName=wechat
getBeansOfType=alipay,wechat
containsBean=true
registerBean=manual-ready
objectInjection=ioc-ready|wechat|wechat
fieldCircularInjection=left->right|right->left
constructorCycleDetection=exampleConstructorCycleLeft -> exampleConstructorCycleRight -> exampleConstructorCycleLeft
```

关闭插件时还会看到：

```text
ExampleReportService 销毁前回调
```

## 使用建议

- Kotlin 属性注入直接写 `@Inject lateinit var foo: Foo` 即可，不需要强制改成 `@field:Inject`
- 如果依赖类型存在多个实现，优先用 `@Named` 或 `@Resource(name = ...)`
- 如果构造函数不止一个，显式写 `@Inject constructor(...)`
- singleton Bean 的字段或方法循环依赖会在早期暴露阶段完成；构造函数循环依赖会在初始化或首次解析时直接失败

## 单元测试

IoC 容器的一大优势是让业务组件可以脱离 Bukkit/TabooLib 运行时进行单元测试。项目提供了 `IocTestContext` 轻量测试上下文，在纯 JUnit 环境中即可完成依赖注入和容器行为验证。

### 配置测试依赖

如果你只是在仓库内部写测试，可以继续直接依赖 `taboolib-ioc-core` 的普通测试源码；如果你希望把这套测试能力稳定提供给外部使用，建议直接依赖新模块 `taboolib-ioc-test`：

```kotlin
dependencies {
    // 生产依赖
    taboo(project(":taboolib-ioc"))

    // 测试依赖
    testImplementation(project(":taboolib-ioc-core"))
    testImplementation(project(":taboolib-ioc-api"))
    testImplementation(project(":taboolib-ioc-annotation"))
    testImplementation(project(":taboolib-ioc-test"))
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.8.1")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.8.1")
}

tasks.withType<Test> {
    useJUnitPlatform()
}
```

### IocTestContext 测试上下文

`IocTestContext` 是一个不依赖 `BeanContainer` 单例的独立容器；在新模块里它会作为稳定测试能力一起发布，每个测试方法创建自己的实例，互不干扰：

```kotlin
val ctx = IocTestContext()
ctx.register(UserRepository::class.java)   // 扫描并注册组件
ctx.register(UserService::class.java)
ctx.registerBean("config", AppConfig())    // 手动注册实例
ctx.initialize()                           // 初始化容器

val service = ctx.getBean(UserService::class.java)  // 获取 Bean
```

### TabooLibIocTest 全链路引导（推荐）

`@TabooLibIocTest` 会在测试启动时模拟 Bukkit 生命周期主链路：

- 生命周期推进（`CONST -> INIT -> LOAD -> ENABLE -> ACTIVE`）
- 结束测试时执行关闭链路（`onDisable -> DISABLE`）

默认不会触发 PrimitiveLoader 自动下载，测试优先使用 Gradle 缓存中的依赖。若你确实需要对齐原始加载器链路，再显式开启：`enablePrimitiveBootstrap = true`。

如果需要可观测日志，打开 `observable = true`：

```kotlin
@TabooLibIocTest(
    DemoService::class,
    targetLifeCycle = LifeCycle.ACTIVE,
    invokePostEnable = true,
    observable = true,
    enablePrimitiveBootstrap = false
)
class DemoTest {

    @IocAutowired
    lateinit var service: DemoService
}
```

更完整的测试支持说明见 [docs/testing.md](docs/testing.md)。

### 测试用例示例

示例插件包含 60+ 个测试用例，覆盖 IoC 容器的全部核心能力：

| # | 测试场景 | 说明 |
|---|---------|------|
| 1 | 构造函数注入 | `@Service` 通过 `@Inject constructor` 获取 `@Repository` 依赖 |
| 2 | 字段注入 | `@Inject lateinit var` 自动注入依赖 |
| 3 | 方法注入 | `@Inject fun bind(dep)` 方法参数自动注入 |
| 4 | `@Named` 限定注入 | 同一接口多个实现时按名称选择 |
| 5 | `@Resource` 方法限定注入 | `@Resource(name = ...)` 指定方法注入的实现 |
| 6 | `@PostConstruct` 回调 | Bean 创建并注入完成后自动调用初始化方法 |
| 7 | `@PreDestroy` 回调 | 容器关闭时调用销毁方法 |
| 8 | `@Prototype` 作用域 | 每次获取都创建新实例 |
| 9 | `@Lazy` 延迟初始化 | 首次获取时才创建，且为单例 |
| 10 | 自定义 `@Scope` | 注册自定义作用域控制 Bean 生命周期 |
| 11 | 手动注册 Bean | `registerBean` 注册的实例可被容器查询 |
| 12 | 接口类型解析 | 通过接口类型获取具体实现 |
| 13 | `getBeansOfType` 聚合查询 | 获取某接口的所有实现 |
| 14 | 字段循环依赖解析 | singleton Bean 的字段循环依赖可正常解析 |
| 15 | 构造函数循环依赖拒绝 | 构造函数循环依赖抛出异常并包含依赖链 |
| 16 | `@PostEnable` 回调 | 在 `invokePostEnable` 后执行且依赖已注入 |
| 17 | `@PostEnable` 执行顺序 | `@PostConstruct` 在 `@PostEnable` 之前执行 |

#### 示例：构造函数注入测试

```kotlin
@Test
fun `构造函数注入 - Service 通过构造函数获取 Repository 依赖`() {
    val ctx = IocTestContext()
    ctx.register(SimpleUserRepository::class.java)
    ctx.register(SimpleUserService::class.java)
    ctx.initialize()

    val service = ctx.getBean(SimpleUserService::class.java)

    assertNotNull(service)
    assertEquals("user-alice", service!!.findUser("alice"))
}
```

#### 示例：@Named 多实现选择测试

```kotlin
@Test
fun `Named限定注入 - 同一接口多个实现时按名称选择`() {
    val ctx = IocTestContext()
    ctx.register(JsonSerializer::class.java)
    ctx.register(XmlSerializer::class.java)
    ctx.register(NamedConsumer::class.java)
    ctx.initialize()

    val consumer = ctx.getBean(NamedConsumer::class.java)

    assertNotNull(consumer)
    assertEquals("json", consumer!!.primaryFormat())
    assertEquals("xml", consumer.secondaryFormat())
}
```

#### 示例：Prototype 作用域测试

```kotlin
@Test
fun `Prototype作用域 - 每次获取都创建新实例`() {
    PrototypeCounter.count = 0
    val ctx = IocTestContext()
    ctx.register(PrototypeCounter::class.java)
    ctx.initialize()

    val first = ctx.getBean(PrototypeCounter::class.java)
    val second = ctx.getBean(PrototypeCounter::class.java)

    assertNotSame(first, second)
    assertEquals(1, first!!.id)
    assertEquals(2, second!!.id)
}
```

#### 示例：字段循环依赖测试

```kotlin
@Test
fun `字段循环依赖 - singleton Bean 的字段循环依赖可正常解析`() {
    val ctx = IocTestContext()
    ctx.register(CycleNodeA::class.java)
    ctx.register(CycleNodeB::class.java)
    ctx.initialize()

    val a = ctx.getBean(CycleNodeA::class.java)
    val b = ctx.getBean(CycleNodeB::class.java)

    assertSame(b, a!!.nodeB)
    assertSame(a, b!!.nodeA)
}
```

完整测试代码见：`taboolib-ioc-example/src/test/kotlin/top/wcpe/ioc/example/ExamplePluginIoCTest.kt`

运行测试：

```bash
./gradlew :taboolib-ioc-example:test
```

## 架构文档

详细的容器架构、启动流程和内部机制说明请参阅 [架构文档](docs/architecture.md)。
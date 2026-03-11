# Taboolib IoC

为 TabooLib Bukkit 插件场景提供的轻量 IoC 容器。

## 当前支持

- 组件标记：`@Component`、`@Service`、`@Repository`、`@Controller`
- 依赖注入：构造函数、字段、方法注入
- 容器初始化：非 lazy singleton 在 `ACTIVE` 阶段预初始化，其他作用域按需创建
- 名称限定：`@Named`、`@Resource`
- 生命周期：`@PostConstruct`、`@PreDestroy`
- 作用域：默认 singleton、`@Prototype`、`@Scope` 与 `registerScope` 自定义作用域
- 扫描控制：`@ComponentScan`
- 懒加载：`@Lazy`
- 循环依赖检测：singleton Bean 的字段/方法循环依赖可解析，构造函数循环依赖会输出依赖链
- Kotlin `object` 自动注入
- 容器查询：`getBean`、`getBeansOfType`、`containsBean`、`getBeanNames`
- 手动注册单例：`registerBean`
- 按接口和父类类型解析 Bean

## 作用域与扫描说明

当前版本已经重新提供并实现以下能力：

- `@Lazy`：仅延迟 Bean 自身的创建，首次被解析时初始化
- `@ComponentScan`：可按包名或基准类限制当前插件 Jar 内的组件扫描范围
- `@Prototype`：每次解析都会创建新实例
- `@Scope("custom")`：配合 `BeanContainer.registerScope(...)` 使用自定义作用域

说明：

- 默认仍是 singleton 单例作用域
- singleton Bean 支持字段/方法循环依赖的早期暴露
- prototype / 自定义作用域 Bean 采用按需创建，不参与容器关闭时的统一 `@PreDestroy`

## 安装

### Gradle (Kotlin DSL)

```kotlin
repositories {
    maven("https://maven.wcpe.top/repository/maven-public/")
}

dependencies {
    compileOnly("top.wcpe.taboolib.ioc:taboolib-ioc:1.0.0-SNAPSHOT")
}
```

### Maven

```xml
<repository>
    <id>wcpe</id>
    <url>https://maven.wcpe.top/repository/maven-public/</url>
</repository>

<dependency>
    <groupId>top.wcpe.taboolib.ioc</groupId>
    <artifactId>taboolib-ioc</artifactId>
    <version>1.0.0-SNAPSHOT</version>
    <scope>provided</scope>
</dependency>
```

## 快速开始

### 1. 定义组件

```kotlin
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
@Service
class LifecycleService {

    @PostConstruct
    fun onInit() {
        println("Bean 初始化完成，依赖注入已执行")
    }

    @PreDestroy
    fun onDestroy() {
        println("容器关闭前执行清理")
    }
}
```

### 5. 从容器获取 Bean

```kotlin
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

### 6. Kotlin object 注入

```kotlin
object PluginState {

    @Inject
    lateinit var userService: UserService

    fun doSomething() {
        userService.getUser("123")
    }
}
```

### 7. 作用域与懒加载

```kotlin
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

## 完整示例

以下是一个完整的插件示例，展示所有核心功能：

```kotlin
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
- `@PostConstruct` / `@PreDestroy`
- Kotlin `object` 自动注入
- `BeanContainer` 全部公开查询/注册方法
- 接口类型 `getBeansOfType` 聚合查询

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




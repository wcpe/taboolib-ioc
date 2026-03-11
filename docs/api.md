# API 文档

本文档只描述当前版本真实可用的公开 API。

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

- 仅作用于 Bean 自身的创建时机
- singleton Bean 会在首次解析时初始化，而不是在容器启动时预初始化
- 不提供注入点级别的代理懒加载

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
### `@Inject`

依赖注入注解。

```kotlin
@Target(AnnotationTarget.CONSTRUCTOR, AnnotationTarget.FIELD, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class Inject
```

支持：

- 构造函数注入
- 字段注入
- 方法注入
- Kotlin `lateinit var` 属性注入

示例：

```kotlin
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

### `@Named`

给 `@Inject` 指定名称限定。

```kotlin
@Target(AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
annotation class Named(val value: String = "")
```

示例：

```kotlin
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
- 一个类最多保留一个有效方法

### `@PreDestroy`

容器关闭时执行。

```kotlin
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class PreDestroy
```

要求：

- 方法无参数
- 一个类最多保留一个有效方法

## 循环依赖

- singleton Bean 的字段注入和方法注入循环依赖会在早期暴露阶段完成
- 构造函数循环依赖会在 `ACTIVE` 初始化阶段抛出 `CircularDependencyException`
- 异常会携带完整依赖链，便于快速定位问题

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
BeanContainer.registerScope("conversation", object : BeanScope {
    private val cache = ConcurrentHashMap<String, Any>()

    override fun get(name: String, definition: BeanDefinition, creator: () -> Any): Any {
        return cache.getOrPut(name, creator)
    }
})
```
## 扫描与生命周期

### 扫描时机

- 组件类在 TabooLib `ENABLE` 阶段扫描
- 扫描源来自当前插件 Jar 的类表
- 不依赖你的业务包名是否包含 `.taboolib.`
- 如果存在 `@ComponentScan`，只会注册命中包范围的组件

### 容器初始化时机

- 容器在 `ACTIVE` 前置任务中初始化
- 非 lazy 的 singleton Bean 会在初始化阶段预先创建
- `@Lazy` singleton、`prototype` 与自定义作用域 Bean 会在首次解析时按需创建
- Kotlin `object` 注入在容器初始化之后、用户 `@Awake(ACTIVE)` 之前执行

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
@Service("customService")
class MyService

@Repository
class UserRepository // bean name: userRepository
```

## Kotlin `object` 自动注入

所有位于插件 Jar 中、字段上带 `@Inject` 或 `@Resource` 的 Kotlin `object` 都会自动注入。

示例：

```kotlin
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




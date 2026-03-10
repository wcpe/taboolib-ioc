# Taboolib IoC

为 TabooLib Bukkit 插件场景提供的轻量 IoC 容器。

## 当前支持

- 组件标记：`@Component`、`@Service`、`@Repository`、`@Controller`
- 依赖注入：构造函数、字段、方法注入
- 名称限定：`@Named`、`@Resource`
- 生命周期：`@PostConstruct`、`@PreDestroy`
- Kotlin `object` 自动注入
- 容器查询：`getBean`、`getBeansOfType`、`containsBean`、`getBeanNames`
- 手动注册单例：`registerBean`
- 按接口和父类类型解析 Bean

## 已移除的伪功能

下面这些能力此前出现在文档里，但实现并不成立，已经从公开 API 中收敛掉：

- `@Lazy`
- `@ComponentScan`
- 自定义 Scope / Prototype 作用域

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

```kotlin
@Repository
class UserRepository {
    fun status(): String = "ready"
}

@Service
class UserService @Inject constructor(
    private val repository: UserRepository
) {

    @Inject
    fun bindFormatter(formatter: TextFormatter) {
        this.formatter = formatter
    }

    @PostConstruct
    fun init() {
        println("UserService 初始化完成")
    }

    private lateinit var formatter: TextFormatter

    fun describe(): String {
        return formatter.line("status", repository.status())
    }
}

@Component
class TextFormatter {
    fun line(label: String, value: Any): String = "$label=$value"
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
- `@PostConstruct` / `@PreDestroy`
- Kotlin `object` 自动注入
- `BeanContainer` 全部公开查询/注册方法
- 接口类型 `getBeansOfType` 聚合查询

核心入口见：

- `taboolib-ioc-example/src/main/kotlin/top/wcpe/taboolib/ioc/example/ExamplePlugin.kt`
- `taboolib-ioc-example/src/main/kotlin/top/wcpe/taboolib/ioc/example/controller/ExampleFeatureController.kt`
- `taboolib-ioc-example/src/main/kotlin/top/wcpe/taboolib/ioc/example/service/ExampleReportService.kt`

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
```

关闭插件时还会看到：

```text
ExampleReportService 销毁前回调
```

## 使用建议

- Kotlin 属性注入直接写 `@Inject lateinit var foo: Foo` 即可，不需要强制改成 `@field:Inject`
- 如果依赖类型存在多个实现，优先用 `@Named` 或 `@Resource(name = ...)`
- 如果构造函数不止一个，显式写 `@Inject constructor(...)`

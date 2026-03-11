---
name: taboolib-ioc-patterns
description: Coding patterns extracted from taboolib-ioc repository
version: 1.0.0
source: local-git-analysis
analyzed_commits: 8
---

# Taboolib IoC Patterns

本项目是一个为 TabooLib Bukkit 插件场景提供的轻量 IoC 容器，使用 Kotlin 实现。

## Commit 约定

项目使用 **Conventional Commits** 格式，提交信息使用中文描述：

| 前缀 | 用途 | 示例 |
|------|------|------|
| `feat(ioc):` | 新功能 | `feat(ioc): 添加 Bean 作用域与懒加载支持` |
| `refactor(ioc):` | 重构 | `refactor(ioc): 重构依赖注入容器实现` |
| `chore(build):` | 构建/维护 | `chore(build): 配置项目发布和子模块设置` |
| `docs(readme):` | 文档更新 | `docs(readme): 更新 README 添加完整快速开始指南` |
| `docs(api):` | API 文档 | `docs(api): 添加安装指南并更新构建配置` |

## 项目结构

多模块 Gradle 项目，基于 TabooLib 框架：

```
taboolib-ioc/
├── taboolib-ioc-annotation/    # 注解定义 (@Component, @Service, @Inject 等)
├── taboolib-ioc-api/           # 公开 API (BeanContainer, BeanDefinition, BeanScope)
├── taboolib-ioc-core/          # 核心实现 (扫描、注入、生命周期管理)
├── taboolib-ioc-example/       # 示例插件，展示全部功能
├── taboolib-ioc/               # 主模块
├── docs/                       # 文档
│   └── api.md                  # API 文档
├── README.md                   # 项目说明与快速开始
└── CHANGELOG.md                # 变更日志
```

## 模块职责

### taboolib-ioc-annotation
纯注解定义，无依赖：
- `@Component`, `@Service`, `@Repository`, `@Controller` - 组件标记
- `@Inject`, `@Named`, `@Resource` - 依赖注入
- `@PostConstruct`, `@PreDestroy` - 生命周期回调
- `@Scope`, `@Prototype`, `@Lazy` - 作用域控制
- `@ComponentScan` - 扫描控制

### taboolib-ioc-api
公开 API 和数据结构：
- `BeanContainer` - 容器入口点
- `BeanDefinition` - Bean 元数据
- `BeanScope` - 自定义作用域接口
- `BeanRegistry` - Bean 注册表

### taboolib-ioc-core
核心实现逻辑：
- `ClassScanner` - 组件扫描
- `Injector` - 依赖注入
- `LifecycleManager` - 生命周期管理
- `CycleDetector` / `CycleResolver` - 循环依赖处理

## 代码风格

### Kotlin 单例模式
使用 `object` 声明单例：

```kotlin
object BeanContainer {
    fun <T> getBean(type: Class<T>, name: String? = null): T?
}
```

### 不可变数据类
使用 `data class` 或不可变属性：

```kotlin
class BeanDefinition(
    val name: String,
    val type: Class<*>,
    val constructor: Constructor<*>,
    // ...
)
```

### 扩展函数
为现有类添加功能：

```kotlin
fun BeanDefinition.isSingletonScope(): Boolean =
    BeanScopes.normalize(scope) == BeanScopes.SINGLETON
```

## 依赖注入模式

### 构造函数注入（推荐）
```kotlin
@Service
class UserService @Inject constructor(
    private val repository: UserRepository
)
```

### 字段注入
```kotlin
@Inject
lateinit var formatter: TextFormatter
```

### 方法注入
```kotlin
@Inject
fun bindLogger(logger: LoggerService) {
    this.logger = logger
}
```

### 名称限定
```kotlin
@Inject
@Named("wechatGateway")
lateinit var gateway: PaymentGateway

@Resource(name = "alipayGateway")
fun bindFallback(gateway: PaymentGateway)
```

## 测试约定

- 测试文件位于 `src/test/kotlin/` 目录
- 测试类命名：`*Test.kt`
- 集成测试覆盖核心功能：`BeanContainerIntegrationTest.kt`

## 文档约定

### README.md
- 项目简介与特性列表
- 安装指南（使用 `taboo()` + `relocate`）
- 快速开始示例（渐进式）
- 完整示例代码
- 使用建议

### docs/api.md
- 安装方式
- 注解 API 文档
- BeanContainer API 文档
- 生命周期说明
- 构造函数解析规则

### CHANGELOG.md
- 遵循 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.0.0/)
- 版本号遵循 [语义化版本](https://semver.org/lang/zh-CN/)
- 分类：新增、变更、清理

## 构建配置

### Gradle (Kotlin DSL)
```kotlin
// 使用 taboo() 打包到插件内
dependencies {
    taboo("top.wcpe.taboolib.ioc:taboolib-ioc:1.0.0-SNAPSHOT")
}

// 重定向包名避免冲突
taboolib {
    relocate("top.wcpe.taboolib.ioc", "top.wcpe.yourplugin.ioc")
}
```

## 工作流程

### 添加新功能
1. 在 `taboolib-ioc-annotation` 添加注解（如需要）
2. 在 `taboolib-ioc-api` 添加公开 API
3. 在 `taboolib-ioc-core` 实现功能
4. 在 `taboolib-ioc-example` 添加示例
5. 更新 `README.md` 和 `docs/api.md`
6. 更新 `CHANGELOG.md`

### 发布流程
- SNAPSHOT 版本自动发布到 Maven 仓库
- 通过 GitHub Actions 自动化构建和发布

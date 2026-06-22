# 测试指南

## 模块职责

- `taboolib-ioc-test`：对外发布的测试支撑模块。
- `taboolib-ioc-example`：示例插件测试，演示注入、生命周期与全链路启动。
- `test-v1_12` / `test-v1_20`：Bukkit 版本兼容测试。

## 可复用能力

`taboolib-ioc-test` 提供以下入口：

- `IocTestContext`：独立 IoC 测试上下文，不依赖 `BeanContainer` 单例。
- `TabooLibIocTest`：类似 `@SpringBootTest` 的测试注解。
- `TabooLibIocTestContext`：TabooLib 生命周期引导上下文。
- `IocAutowired`：测试字段自动注入标记。

## 启动链路

默认测试流程会模拟以下顺序：

1. `CONST`
2. `INIT`
3. `LOAD`
4. `ENABLE`
5. `ACTIVE`
6. 关闭时执行 `onDisable`
7. `DISABLE`

如果启用 `observable = true`，测试运行时会输出每一步的观测日志，方便排查生命周期顺序。

默认不会触发 PrimitiveLoader 自动下载，测试优先使用 Gradle 缓存中的依赖。若你确实需要对齐原始加载器链路，再显式开启 `enablePrimitiveBootstrap = true`。

## 依赖方式

推荐在测试模块中加入：

```kotlin
dependencies {
    testImplementation(project(":taboolib-ioc-core"))
    testImplementation(project(":taboolib-ioc-api"))
    testImplementation(project(":taboolib-ioc-annotation"))
    testImplementation(project(":taboolib-ioc-test"))
}
```

## 示例

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

## 发布说明

当前对外发布的模块包括：

- `taboolib-ioc`
- `taboolib-ioc-annotation`
- `taboolib-ioc-api`
- `taboolib-ioc-core`
- `taboolib-ioc-test`

示例模块和 `test-v1_12` / `test-v1_20` 仅用于仓库内部验证，不参与发布.

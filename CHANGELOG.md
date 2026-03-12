# Changelog

本项目的所有重要变更都将记录在此文件中。

格式基于 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.0.0/)，
版本号遵循 [语义化版本](https://semver.org/lang/zh-CN/)。

## [1.1.0-SNAPSHOT] - 2026-03-12

### 新增

- `@PostEnable` 生命周期注解：标记在 Bean 方法上，在插件 ENABLE 阶段、所有 Bean 创建完毕且 object 注入完成后统一执行
  - 执行时序：`@PostConstruct`（Bean 创建时）→ object 注入 → `@PostEnable`（ENABLE -80）→ 用户 `@Awake(LifeCycle.ENABLE)`
  - 适用于需要在所有 Bean 就绪后才能执行的初始化逻辑
- Kotlin 扩展方法：`bean<T>()`、`beanOrNull<T>()`、`beans<T>()`，提供更简洁的 Bean 获取方式
- `@Primary` 注解：同类型多 Bean 时标记首选，`getBean` 按类型解析时优先返回
- `@Order` 注解：控制 `getBeansOfType` 返回顺序和 AOP Advisor 执行顺序，值越小优先级越高
- `@Value` 属性注入：支持 `${property:default}` 表达式从系统属性注入值，支持 String/Int/Long/Double/Float/Boolean 类型
- Bean 事件机制：`EventBus` + `BeanCreatedEvent`/`BeanDestroyedEvent`/`ContainerInitializedEvent`/`ContainerShutdownEvent`
- 注入失败时输出 warning 日志，包含类名、字段名、类型和名称限定信息

### 重构

- 提取 `BeanResolver` 类，消除 `BeanContainer` 和 `IocTestContext` 之间的 Bean 解析重复代码

### 修复

- `BeanRegistry.definitionsByType` 线程安全：内部列表改为 `CopyOnWriteArrayList`
- `LifecycleManager` 集合线程安全：`initializationOrder` 改为 `synchronizedList`，`initializedSingletons` 改为 `ConcurrentHashMap.newKeySet`
- 文档修正：README 中 `ACTIVE` 改为 `ENABLE`、安装示例版本号统一、api.md `@Lazy` 描述更新为支持字段级代理懒加载

## [1.0.0-SNAPSHOT] - 2026-03-11

### 新增

- 新增面向 `BeanContainer` 的集成测试，覆盖容器查询、手动注册、字段循环依赖与构造函数循环依赖
- 示例插件新增循环依赖展示，包含可解析的字段循环依赖和会失败的构造函数循环依赖检测
- README 新增完整的快速开始指南，包含 7 个渐进式示例和完整插件示例
- AOP 支持：`@Aspect`、`@Before`、`@After`、`@Around`、`@Pointcut` 注解，基于 JDK 动态代理
  - 支持简化版 `execution()` 切点表达式（精确匹配、通配符、包通配符）
  - `@Pointcut` 可复用切点定义，通知注解可通过方法名引用
  - `@Around` 支持拦截器链、短路、修改返回值
  - `@After` 在目标方法抛出异常时仍会执行
  - 切面 Bean 优先初始化，确保普通 Bean 创建时 AOP 代理已就绪
- 条件装配：`@Conditional`、`@ConditionalOnClass`、`@ConditionalOnMissingClass`、`@ConditionalOnBean`、`@ConditionalOnMissingBean`、`@ConditionalOnProperty`
  - 两阶段评估：扫描时评估类/属性条件，注册后评估 Bean 依赖条件
  - 支持自定义 `Condition` 接口实现
  - 多条件之间为 AND 关系
- 内置作用域扩展：`@ThreadScope`（线程级作用域）、`@RefreshScope`（可刷新作用域）
  - `BeanContainer.refreshScope()` 支持运行时刷新 Bean
  - `BeanContainer.getThreadScope()` 支持手动清理线程缓存
  - 容器初始化时自动注册，无需手动调用 `registerScope`
- 62 个单元测试覆盖 AOP（23）、条件装配（25）、作用域（14）

### 变更

- 容器初始化流程调整为两阶段装配：扫描期注册元数据，`ACTIVE` 阶段统一实例化、注入并执行 `@PostConstruct`
- 循环依赖检测升级为依赖图检测，异常消息会输出完整依赖链
- README 与 API 文档更新为当前真实包路径与公开能力说明
- 安装方式改为使用 `taboo()` 打包到插件内，并要求配置 `relocate` 重定向包名
- 移除 Maven 安装方式，仅支持 Gradle

### 清理

- 移除未再使用的早期单例缓存路径
- 删除未使用的构造函数注入状态判断逻辑
- 更新 README / API 文档，使 `@Lazy`、`@ComponentScan`、自定义 Scope 描述与当前实现一致

---

## 版本规划

### [1.1.0] - 计划中
- 正式版本发布
- 完善文档
- 性能优化

### [1.2.0] - 计划中
- `@Configuration` + `@Bean` Java Config 支持
- Bean 生命周期事件
- 更多扩展点


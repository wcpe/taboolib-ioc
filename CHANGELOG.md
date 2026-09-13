# Changelog

本项目的所有重要变更都将记录在此文件中。

格式基于 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.0.0/)，
版本号遵循 [语义化版本](https://semver.org/lang/zh-CN/)。

## [1.3.0] - 2026-09-13

### 新增

- AOP 静态诊断规则组（配套 Gradle 插件）：`pointcut-target-not-found`、`aop-private-method-pointcut`、`aop-static-method-pointcut`、`aop-target-not-proxied`、`aop-factory-bean-interface-return`、`advice-signature-invalid`

### 修复

- **AOP 具体类注入点不再静默 `null`**：切面命中未实现接口的具体类时，`@Lazy` 具体类回退补齐 `required` 语义并抛出与普通 `@Inject` 一致的异常；目标类型因代理而类型不匹配时给出「`@Lazy` 仅支持接口类型」的明确指引
- 容器初始化前手动注册的 Bean 不再以未注入裸实例提前暴露（改为入队，容器可用时先补全生命周期再暴露），消除半成品注入窗口
- 生命周期管理器：收窄 `getOrCreateSingleton` 全局锁持锁范围，锁内只做环检测登记与实例化，`@PostConstruct` / AOP 等用户代码移到锁外，消除主线程被串行化与多线程死锁
- 线程作用域：`ThreadBeanScope` 移除 fastPath 双结构、改单 registry 并在同一临界区完成读写，与 `clearAllThreads` 线性化，消除线程池复用线程场景下的 TOCTOU 逃逸 / 泄漏窗口（保留 `WeakHashMap` 弱引用语义）
- 扫描层：修复硬编码 `Class.forName("taboolib.common.io.ProjectScannerKt")` 在 Gradle 插件 relocate 后静默失效的问题，改用编译期锚点 `LifeCycle` 推导运行时包名，所有失败路径软降级（告警 + 返回空列表）
- 多 `@Inject` 构造器改为实例化期按确定性顺序逐个尝试解析，全部失败才抛出并列出各候选失败原因（此前仅取静态期首选构造器）
- 手动注册的 `@Aspect` 的 `isAspect` 由硬编码 `false` 改为按实例类判定，修复手动切面通知 100% 静默失效
- AOP：切点表达式解析失败不再冒泡（仅跳过该通知并告警）、通知方法签名扫描期校验、切点仅命中 `static` 方法时告警
- `@Bean` 方法返回 `void` 由抛异常改为告警 + 跳过；`getBeansOfType` 守卫与 `getBean` 对齐；`resetForTesting` 补清 `eventBus` 与 `pendingManualBeans`
- 3 个 Kotlin 元数据缺陷（`$annotations` 载体被 `ACC_SYNTHETIC` 一刀切过滤、含 `$` 的嵌套类被整体跳过、`companion object` 注入点跨类不可见）导致静态规则召回率减半，均已修复（配套 Gradle 插件）

### 变更

- `io.izzel.taboolib` 统一至 `2.0.38-wcpe.1`
- E2E 从 run-paper 迁移到 mc-testkit（0.9.0 自测模式），三模块（`test-v1_20` / `test-v1_12` / `taboolib-ioc-example`）真机起服验证，判定真源为结果文件 `build/mc-testkit/results/smoke.properties` 的 `status=PASS`
- CI 改为 push（全分支）/ PR 即构建 + 测试，并修复失效的起服验证（改用 mc-testkit `e2eSmoke` 矩阵）；发版流水线新增独立测试门禁、排除空壳产物、修正资产命名与 prerelease 判据
- 依赖仓库顺序调整并补充本地代理直连名单（`nonProxyHosts`）
- 重写两处伪测试（`CompanionObjectInjectorTest` / `ObjectInjectorTest`）并补齐缺陷回归用例，core 287 tests 全绿

## [1.1.0] - 2026-03-14

### 新增

- `@Configuration` + `@Bean` Java Config 支持
  - `@Bean` 方法参数支持 `@Named` 限定符和 `@Lazy`
  - `@Bean` 产物支持 `@PostConstruct`/`@PostEnable`/`@PreDestroy` 生命周期回调
  - `@Bean` 产物支持 `@Value`/`@Inject` 字段注入
  - `@Bean` 方法级别条件注解支持（`@ConditionalOnClass`/`@ConditionalOnProperty`/`@ConditionalOnBean` 等可用于 `@Bean` 方法）
  - `@Bean` 返回接口类型时运行时补充扫描实际实现类的注入点和生命周期回调
- 支持多个 `@PostConstruct`/`@PostEnable`/`@PreDestroy` 方法
- Kotlin `companion object` 自动注入：支持在 `companion object` 中使用 `@Inject`/`@Resource` 注解注入字段，兼容 `@JvmField` 和非 `@JvmField` 两种写法，支持 `@Named` 名称限定和 `@Lazy` 代理
- `@Inject` 增加 `required` 参数（`required=false` 时注入失败不抛异常）
- `BeanPostProcessor` 扩展点（before/after initialization 回调）
- `@DependsOn` 显式初始化顺序控制
- `@Value` 支持 `@PropertySource` 配置文件加载（`.properties` 和简单 `.yml`）
- `@PostEnable` 生命周期注解：标记在 Bean 方法上，在插件 ENABLE 阶段、所有 Bean 创建完毕且 object 注入完成后统一执行
  - 执行时序：`@PostConstruct`（Bean 创建时）→ object 注入 → `@PostEnable`（ENABLE -80）→ 用户 `@Awake(LifeCycle.ENABLE)`
  - 适用于需要在所有 Bean 就绪后才能执行的初始化逻辑
- Kotlin 扩展方法：`bean<T>()`、`beanOrNull<T>()`、`beans<T>()`，提供更简洁的 Bean 获取方式
- `@Primary` 注解：同类型多 Bean 时标记首选，`getBean` 按类型解析时优先返回
- `@Order` 注解：控制 `getBeansOfType` 返回顺序和 AOP Advisor 执行顺序，值越小优先级越高
- `@Value` 属性注入：支持 `${property:default}` 表达式从系统属性注入值，支持 String/Int/Long/Double/Float/Boolean 类型
- Bean 事件机制：`EventBus` + `BeanCreatedEvent`/`BeanDestroyedEvent`/`ContainerInitializedEvent`/`ContainerShutdownEvent`
- 注入失败时输出 warning 日志，包含类名、字段名、类型和名称限定信息
- AOP 无接口类输出 warning 日志
- 新增架构文档 `docs/architecture.md`

### 重构

- 提取 `BeanResolver` 类，消除 `BeanContainer` 和 `IocTestContext` 之间的 Bean 解析重复代码

### 修复

- `FieldInjector` 多 Bean 时绕过 `@Primary` 的 bug
- 多个 `@Primary` 时从静默选择改为抛 `IllegalStateException`
- `BeanContainer.shutdown`/`resetForTesting` 清理 `ValueResolver` 全局状态
- `LifecycleManager.shutdown` 中 `preDestroy` 的 null 实例保护
- `BeanRegistry.definitionsByType` 线程安全：内部列表改为 `CopyOnWriteArrayList`
- `LifecycleManager` 集合线程安全：`initializationOrder` 改为 `synchronizedList`，`initializedSingletons` 改为 `ConcurrentHashMap.newKeySet`
- 文档修正：README 中 `ACTIVE` 改为 `ENABLE`、安装示例版本号统一、api.md `@Lazy` 描述更新为支持字段级代理懒加载

## [1.0.0] - 2026-03-11

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

## [1.0.0-SNAPSHOT] - 2026-03-10

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

### [1.2.0] - 计划中
- CGLIB 代理支持（无接口类 AOP）
- `@Async` 异步方法
- Bean 定义覆盖策略

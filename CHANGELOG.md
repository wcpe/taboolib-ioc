# Changelog

本项目的所有重要变更都将记录在此文件中。

格式基于 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.0.0/)，
版本号遵循 [语义化版本](https://semver.org/lang/zh-CN/)。

## [未发布]

### 新增

- **编译期 AOP 织入（可选，默认关闭）**：配套 Gradle 插件新增 `taboolibIoc { weaving(true) }` 开关与 `weaveTaboolibIocAop` 任务（挂在 `jar` / `assemble` / `build` / `taboolibMainTask` 之前）。开启后被切点命中的 public 实例方法在**构建期**被改写为转发到 `AopWeavingRuntime`，原方法体搬到合成方法 `xxx$ioc$original`：
  - **具体类（不实现任何接口）也能被切面命中，且不创建任何代理** —— 每次调用只多一次装箱 + 一次静态跳转；
  - 织入后的类被加上 `WovenTarget` 标记接口（**relocate 安全**：类自身字节码会被 relocate 一并重写，资源文件里的类名不会），运行期 `AopProxyFactory` 见到它即跳过代理，避免通知执行两次；
  - 幂等（已织入的类不再处理）；不改写 `static` / `private` / `abstract` / `native` / 合成方法，也不改写构造器；`@NoAspect` 的类与方法跳过；
  - **运行期入口零反射**：woven 方法传的 key（**方法名 + 描述符**）在**构建期**算好，运行期按 `ClassValue<Map<key, 入口>>` 查表缓存（入口含预热通知链），不再每次调用反射解析方法或拼接字符串 —— 实测该缺陷会让真机稳态从 63.5 ns/op 劣化到 886 ns/op；
  - 只用 ASM（`asm` + `asm-tree`，**构建期依赖**，不进消费者插件 jar）；
  - 真机验证：示例插件中一个不实现接口的具体类，未开启时 `切面命中=0` + `AOP 代理跳过` 日志，开启后 `切面命中=1` 且对象类名不是 `$Proxy`。
- 运行期新增 `AopWeavingRuntime`（织入入口）、`WovenTarget`（标记接口）、`AopExclusions`（`@NoAspect` 判定的单一实现，代理与织入共用）、`AopPlans`（通知链构建，代理与织入共用）。
- **`@NoAspect`**：声明式排除 AOP。标注在类上 → 该 Bean 完全不创建代理（零额外开销）；标注在方法上 → 该方法不参与切点匹配（接口方法与实现方法都会检查）。用于把每 tick / 高频调用的方法从切面里摘出去。
- **`@WrapWith`**：用手写装饰器包装 Bean（零运行期开销的组合方式）。容器在 `@PostConstruct` 之后、AOP 代理之前把实例换成装饰器；调用路径上没有任何额外开销，适合少量高频切点。
- `test-v1_20` 内置**基准与压力测试套件**（`top.wcpe.taboolib.ioc.test.v20.bench`）：单线程吞吐、并发扩展性（1/2/4/8/16 线程）、延迟分位、8 线程持续压力、压力前后内存对比、AOP 三条调用路径开销对比。支持 `/iocbench [scale]` 命令触发，也支持 `IOC_BENCH_AUTORUN` / `IOC_BENCH_OUT` / `IOC_BENCH_SCALE` / `IOC_BENCH_SHUTDOWN` 等环境变量（或同名 `-Dioc.bench.*` 属性）全自动运行，结果写 JSON 便于出图
- README 新增「基准与压力测试」章节：实测数据表、五张图表、口径与边界说明；图表由 `docs/benchmark/make_charts.py` 从结果 JSON 生成。另附 `docs/benchmark/AopCost.java`（零依赖单文件微基准，拆解 AOP 调用开销）

### 性能

- **AOP 热路径优化**（真机实测代理调用 **111.6 → 47.1 ns/op，−58%**；相对普通调用从 45.1x 降到 19.4x）：
  - 切点匹配与「按通知类型建链」从**每次调用**改为**代理创建期一次**，按方法缓存 `AopPlan`；
  - 切面注册表新增版本号，运行期新注册切面（手动注册路径）会让缓存自动失效重建，语义不变；
  - `MethodInvocation` 改为线程局部**池化复用**（嵌套调用各自取实例），热路径不再每次都分配；
  - 通知与目标方法调用改用预热的 `MethodHandle`，无法 unreflect 时自动回退 `Method.invoke`（行为与旧实现一致）；
  - 未被切点命中的方法在代理上也有预热直达路径。
- **类型索引缓存**（`BeanRegistry.getByType()`，真容器实测每次调用分配 **51.1 B → 3.1 B，−94%**）：
  - 原先每次调用都 `toList() + sortedBy { it.order }`（新建两个 ArrayList 再排序）；现改为**写入侧重建不可变快照、读取侧一次 volatile 读** —— 注册 / 移除是启动期低频繁操作，由它们承担重建成本，读路径零分配零排序；
  - 缓存失效用「同一把锁同时保护定义列表与快照」实现：若只在写入侧加锁，读者可能在变更后把过期快照写进缓存并永久留下；
  - `getPrimaryByType` 的多候选分支由 `filter { it.isPrimary }` 改为内联扫描，仅在真的存在第二个 `@Primary` 时才回到 filter 去拼报错信息；
  - 快照用 `Collections.unmodifiableList` 包装以挡住外部误改（原实现每次返回新副本，改成共享快照后一旦被改就是静默污染）；代价是枚举型 API 迭代时多创建一个 iterator 包装对象，`getBeansOfType` 因此 +32 B/op —— 该类 API 本就是分配大户且已声明不适合热循环，故保留安全性；
  - **不宣称 ns/op 收益**：48 B/次折合约个位数 ns，落在本基准 ±15% 的轮间噪声内（同一份代码交错 4 轮，未改动路径也会飘 ±7~14%）。离线单独测量（`docs/benchmark/TypeIndexCost.java`）为 18.1→2.7 ns/op（1 候选）、23.6→2.7 ns/op（3 候选）；
  - 基准套件新增**确定性指标「每次调用分配字节数」**（`bytesPerOp()`，用 `ThreadMXBean.getThreadAllocatedBytes`）：不受调度 / GC 时机影响，用于判定优化是否真的省掉了分配，也能识别「临时对象已被逃逸分析消除、改动只停留在纸面上」的情形。

### 变更

- `MethodInvocation` 由 `final` 改为 `open`、属性改为可写（**源码与二进制兼容**，构造签名与字段描述符不变），以支持容器内部的零分配复用
- `InterceptorChain` 标记 `@Deprecated`：逻辑已被 `AopPlan` 取代（不再被容器使用，保留以兼容直接引用它的外部代码）
- 内置 `BeanPostProcessor`（`@WrapWith`）由 `LifecycleManager` 自身持有，扫描路径与 `IocTestContext` 路径语义一致

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

## [1.2.0] - 2026-06-22

> **回溯补录**：1.2.0 发布时（提交 `0e71861`）未随版本写入 CHANGELOG，本节现依据 `c25a96b`（1.1.0 发布提交）至 `0e71861` 之间的提交逐条核实后补入。

### 新增

- 拆出独立测试支撑模块 `taboolib-ioc-test`：将 `IocTestContext` 与 TabooLib 测试注解从 core 的 `testFixtures` 迁移为对外发布模块，供外部项目直接复用；`taboolib-ioc-example` / `test-v1_12` / `test-v1_20` 的测试依赖同步切换
- 示例模块补充启动链路验证用例，覆盖 `TabooLibIocTest` 自动注入与 `CONST`→`DISABLE` 启动链路在示例模块中的可用性
- `test-v1_12` / `test-v1_20` 各新增 mock 插件主源集与 12 套测试，覆盖 Bean 注册、注入、`@Primary`/`@Order`、条件装配、作用域、生命周期、`@Lazy`、AOP、`@Value`/`@PropertySource`、`BeanPostProcessor` 与 MockBukkit 集成（v1_12 共 136、v1_20 共 137 个用例）
- 新增 `BeanInstantiationException`，为构造函数/方法注入的参数校验提供清晰错误信息
- 新增 `BeanRegistry.remove()`、`ClassScanner` 公开扫描入口、`LifecycleManager.getBeanPostProcessors()` / `recordInitialization()` 等方法
- 新增 `docs/testing.md` 测试支撑模块指南（模块职责、可复用能力、启动链路与可观测开关）

### 修复

- **`RefreshBeanScope` 缺少销毁回调**：`refresh()` / `clear()` 现调用 `@PreDestroy`，防止资源泄漏
- **手动注册 Bean 生命周期**：`registerBean()` 改为执行完整生命周期（属性注入、`BeanPostProcessor`、`@PostConstruct`、AOP 代理）
- **参数校验**：构造函数参数为 `null` 时抛出 `BeanInstantiationException` 并携带详细调试信息；方法注入参数为 `null` 时同样抛异常而非仅告警
- **循环依赖检测范围**扩展至所有作用域，日志区分可解析 / 不可解析依赖
- **`shutdown()` 清理 `singletonLocks`**，避免锁对象长期驻留导致内存占用增长
- **跨平台注入反射崩溃**：`findAnnotationCarrier` 反射 `declaredMethods` 捕获 `NoClassDefFoundError`，`injectObjectFields` 两处 `catch(Exception)` 放宽为 `catch(Throwable)`，避免错误平台宿主（如 Bukkit 上加载 Bungee 事件）导致整个插件 enable 失败
- **object 注入器平台过滤**：`collectObjectClasses` 增加 `@PlatformSide` 门控，从收集阶段跳过不匹配的宿主
- **`Injector.invokePostConstruct`**：修复反射异常包装导致 `@PostConstruct` 抛出的 `RuntimeException` 被吞为 `InvocationTargetException`
- **`ThreadBeanScopeTest` 内存泄漏断言**：改为按去重后实例数判断，避免 `WeakReference` 未被 GC 时误报
- **CI 构建任务名**：修正不存在的 `taboolibBuildPlugin`，统一改用 `taboolibMainTask`；CI 以 `:<module>:assemble` 串联 `jar → taboolibMainTask`，避免漏跑前置任务

### 变更

- 版本切至 `1.2.0-SNAPSHOT`，同步 Kotlin 标识与架构文档；清理已迁移至独立模块的 core `testFixtures` 入口
- 示例模块改用 `id("top.wcpe.taboolib.ioc")` Gradle 插件自动处理 relocate，移除手动 `relocate(...)` 与 `taboo(project(":taboolib-ioc"))`；根工程以 `apply false` 声明插件 `0.0.6`，`settings.gradle.kts` 的 `pluginManagement` 增补解析仓库
- CI 新增自动测试流水线：`push` / PR 触发 `./gradlew test --continue` 并跨模块汇总；`aggregate-test-results.py` 解析各模块 JUnit XML 生成 Markdown 报告并写入 Step Summary；上传 JUnit XML / HTML / Markdown 三类 artifact，并通过 `EnricoMi/publish-unit-test-result-action` 在 Checks 页签呈现结果
- CI 新增 1.12.2 / 1.20.4 真实服务器烟雾测试矩阵（`server-smoke.sh` 下载对应版本服务端、部署插件、扫描日志关键字判定成功后优雅退出）
- 测试规模：相较 1.1.0，本版新增 55+ 单元测试（并发初始化、手动注册生命周期、作用域循环依赖、构造参数校验、锁清理、Refresh 销毁、ThreadLocal 泄漏等）

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

### 待评估（尚未纳入任何版本）
- CGLIB 代理支持（无接口类 AOP）
- `@Async` 异步方法
- Bean 定义覆盖策略

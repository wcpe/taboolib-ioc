# Changelog

本项目的所有重要变更都将记录在此文件中。

格式基于 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.0.0/)，
版本号遵循 [语义化版本](https://semver.org/lang/zh-CN/)。

## [1.0.0-SNAPSHOT] - 2026-03-10

### 新增

#### 注解模块
- `@Component` - 通用组件注解，支持自定义 Bean 名称
- `@Service` - 服务层组件注解
- `@Repository` - 数据访问层组件注解
- `@Controller` - 控制器层组件注解
- `@Inject` - JSR-330 标准依赖注入注解
- `@Resource` - JSR-250 标准资源注入注解，支持按名称匹配
- `@Named` - 配合 `@Inject` 指定 Bean 名称
- `@Lazy` - 延迟注入注解，用于打破循环依赖
- `@PostConstruct` - Bean 初始化后回调注解
- `@PreDestroy` - Bean 销毁前回调注解
- `@ComponentScan` - 组件扫描范围配置注解

#### 作用域模块
- `Scope` 接口 - 作用域抽象接口
- `SingletonScope` - 单例作用域实现
- `PrototypeScope` - 原型作用域实现
- `BeanScope` 枚举 - 作用域类型定义

#### Bean 管理模块
- `BeanDefinition` - Bean 元数据定义类
- `BeanRegistry` - Bean 注册表，支持按名称和类型查询
- `BeanContainer` - Bean 容器主类，提供获取、注册、检查 Bean 等 API

#### 依赖注入模块
- `ConstructorResolver` - 构造函数解析器，支持自动选择合适构造函数
- `FieldInjector` - 字段注入器，处理字段和方法注入
- `Injector` - 注入器主类，协调构造函数和字段注入
- `ObjectInjector` - object 类自动注入器，支持 Kotlin object 单例类字段自动注入

#### 类扫描模块
- `ClassScanner` - 类扫描器，解析类元数据并创建 BeanDefinition
- `ComponentScanResolver` - 扫描配置解析器
- `ComponentVisitor` - Taboolib ClassVisitor 集成，自动扫描组件

#### 循环依赖处理模块
- `CycleDetector` - 循环依赖检测器
- `CycleResolver` - 循环依赖解析器，使用二级缓存
- `LazyProxy` - 延迟注入代理
- `CircularDependencyException` - 循环依赖异常，提供清晰的错误信息

#### 生命周期管理模块
- `LifecycleManager` - 生命周期管理器，处理初始化和销毁
- `ContainerLifecycle` - Taboolib 生命周期集成

### 特性

- **构造函数注入** - 自动选择 `@Inject` 标记、全参数或无参构造函数
- **字段注入** - 支持 `@Inject` 和 `@Resource` 注解
- **方法注入** - 支持 setter 方法注入
- **循环依赖处理** - 字段/方法注入支持循环依赖（二级缓存），构造函数注入检测并报错
- **延迟注入** - 通过 `@Lazy` 注解打破循环依赖
- **按名称匹配** - 支持 `@Resource(name)` 和 `@Inject` + `@Named` 指定 Bean 名称
- **自动扫描** - 集成 Taboolib ClassVisitor，无需手动注册
- **生命周期回调** - 支持 `@PostConstruct` 和 `@PreDestroy`
- **Object 自动注入** - Kotlin `object` 单例类中带有注入注解的字段会自动注入

### 测试

- 添加完整的单元测试覆盖
- 测试模块：scope、bean、cycle、inject、scan

---

## 版本规划

### [1.0.0] - 计划中
- 正式版本发布
- 完善文档
- 性能优化

### [1.1.0] - 计划中
- AOP 支持
- 条件装配 `@Conditional` 系列
- 更多作用域支持

### [1.2.0] - 计划中
- `@Configuration` + `@Bean` Java Config 支持
- Bean 生命周期事件
- 更多扩展点

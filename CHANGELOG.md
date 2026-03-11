# Changelog

本项目的所有重要变更都将记录在此文件中。

格式基于 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.0.0/)，
版本号遵循 [语义化版本](https://semver.org/lang/zh-CN/)。

## [1.0.0-SNAPSHOT] - 2026-03-11

### 新增

- 新增面向 `BeanContainer` 的集成测试，覆盖容器查询、手动注册、字段循环依赖与构造函数循环依赖
- 示例插件新增循环依赖展示，包含可解析的字段循环依赖和会失败的构造函数循环依赖检测
- README 新增完整的快速开始指南，包含 7 个渐进式示例和完整插件示例

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


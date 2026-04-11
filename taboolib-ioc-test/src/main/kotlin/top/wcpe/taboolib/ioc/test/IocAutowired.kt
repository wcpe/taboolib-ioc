package top.wcpe.taboolib.ioc.test

/**
 * 测试字段自动注入标记。
 *
 * - 字段类型是 TabooLibIocTestContext 时注入测试上下文。
 * - 其他类型按 IoC 容器中的 Bean 类型注入。
 */
@Target(AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
annotation class IocAutowired
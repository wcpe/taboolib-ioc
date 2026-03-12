package top.wcpe.taboolib.ioc.annotation

/**
 * 声明当前 Bean 依赖于指定的 Bean，确保它们在当前 Bean 之前初始化。
 *
 * @property value 依赖的 Bean 名称列表
 */
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class DependsOn(vararg val value: String)

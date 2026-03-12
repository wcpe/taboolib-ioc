package top.wcpe.taboolib.ioc.annotation

/**
 * Bean 工厂方法注解。
 *
 * 在 @Configuration 类中标记一个方法为 Bean 工厂方法。
 * 方法的返回值将被注册为容器中的 Bean。
 *
 * @param value Bean 名称，默认为方法名
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class Bean(val value: String = "")

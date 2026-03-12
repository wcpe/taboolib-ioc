package top.wcpe.taboolib.ioc.annotation

/**
 * 配置类注解。
 *
 * 标记一个类为配置类，其中的 @Bean 方法将被解析为 Bean 定义。
 * 配置类本身也会被注册为组件。
 *
 * @param value Bean 名称，默认为类名首字母小写
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class Configuration(val value: String = "")

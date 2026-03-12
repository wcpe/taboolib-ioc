package top.wcpe.taboolib.ioc.annotation

/**
 * 指定配置文件来源。
 * 标注在 @Configuration 类上，加载指定的配置文件。
 * 支持 .properties 和 .yml 格式。
 *
 * @property value 配置文件路径列表（classpath 相对路径）
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class PropertySource(vararg val value: String)

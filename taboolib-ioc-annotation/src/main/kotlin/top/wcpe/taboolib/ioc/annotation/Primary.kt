package top.wcpe.taboolib.ioc.annotation

/**
 * 标记首选 Bean
 * 当同一类型存在多个 Bean 时，优先选择标记了 @Primary 的 Bean
 */
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class Primary

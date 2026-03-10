package top.wcpe.taboolib.ioc.annotation

/**
 * 配合 @Inject 使用，指定 Bean 名称
 */
@Target(AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
annotation class Named(val value: String = "")

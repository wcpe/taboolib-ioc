package top.wcpe.taboolib.ioc.annotation

/**
 * JSR-330 标准注入注解
 * 按类型匹配，可配合 @Named 指定名称
 */
@Target(AnnotationTarget.CONSTRUCTOR, AnnotationTarget.FIELD, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class Inject

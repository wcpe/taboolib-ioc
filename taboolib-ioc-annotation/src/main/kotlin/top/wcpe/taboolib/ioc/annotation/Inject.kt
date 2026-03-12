package top.wcpe.taboolib.ioc.annotation

/**
 * JSR-330 标准注入注解
 * 按类型匹配，可配合 @Named 指定名称
 *
 * @property required 是否必须注入成功，为 true 时注入失败将抛出异常，为 false 时注入失败字段保持 null
 */
@Target(AnnotationTarget.CONSTRUCTOR, AnnotationTarget.FIELD, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class Inject(val required: Boolean = true)

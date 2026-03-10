package top.wcpe.taboolib.ioc.annotation

/**
 * JSR-250 标准资源注入注解
 * @param name Bean 名称，指定后按名称匹配
 */
@Target(AnnotationTarget.FIELD, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class Resource(val name: String = "")

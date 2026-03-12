package top.wcpe.taboolib.ioc.annotation

/**
 * 当指定的系统属性匹配时，Bean 才会被注册。
 *
 * @param name 系统属性名称
 * @param havingValue 期望的属性值，为空字符串时仅检查属性是否存在
 * @param matchIfMissing 属性不存在时是否视为匹配，默认 false
 */
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class ConditionalOnProperty(
    val name: String,
    val havingValue: String = "",
    val matchIfMissing: Boolean = false
)

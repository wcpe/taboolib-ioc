package top.wcpe.taboolib.ioc.annotation

/**
 * 属性值注入
 * 支持从系统属性中注入值，格式：${property.name:defaultValue}
 *
 * @property value 属性表达式
 */
@Target(AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
annotation class Value(val value: String)

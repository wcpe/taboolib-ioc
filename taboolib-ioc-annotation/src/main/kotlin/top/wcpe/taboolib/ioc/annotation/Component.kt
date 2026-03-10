package top.wcpe.taboolib.ioc.annotation

/**
 * 通用组件注解
 * @param value Bean 名称，默认为类名首字母小写
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class Component(val value: String = "")

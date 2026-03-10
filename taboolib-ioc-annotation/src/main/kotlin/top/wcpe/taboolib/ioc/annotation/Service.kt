package top.wcpe.taboolib.ioc.annotation

/**
 * 服务层组件
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class Service(val value: String = "")

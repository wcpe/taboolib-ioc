package top.wcpe.taboolib.ioc.annotation

/**
 * 控制器层组件
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class Controller(val value: String = "")

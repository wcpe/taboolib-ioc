package top.wcpe.taboolib.ioc.annotation

/**
 * 数据访问层组件
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class Repository(val value: String = "")

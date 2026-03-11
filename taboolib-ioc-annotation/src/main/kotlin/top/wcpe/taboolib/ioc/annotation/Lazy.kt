package top.wcpe.taboolib.ioc.annotation

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class Lazy(val value: Boolean = true)

package top.wcpe.taboolib.ioc.annotation

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class Scope(val value: String = "singleton")

package top.wcpe.taboolib.ioc.annotation

/**
 * 线程作用域快捷注解，等价于 `@Scope("thread")`。
 *
 * 每个线程持有独立的 Bean 实例。
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class ThreadScope

package top.wcpe.taboolib.ioc.annotation

/**
 * 可刷新作用域快捷注解，等价于 `@Scope("refresh")`。
 *
 * Bean 实例会被缓存，可通过容器 API 触发刷新重建。
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class RefreshScope

package top.wcpe.taboolib.ioc.annotation

/**
 * 控制 Bean 的排序优先级
 * 值越小优先级越高，默认为 Int.MAX_VALUE
 * 影响 getBeansOfType 返回顺序和 AOP Advisor 执行顺序
 */
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class Order(val value: Int = Int.MAX_VALUE)

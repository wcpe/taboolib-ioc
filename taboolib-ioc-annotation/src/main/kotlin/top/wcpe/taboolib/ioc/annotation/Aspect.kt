package top.wcpe.taboolib.ioc.annotation

/**
 * 标记一个类为切面。
 *
 * 切面类中可以定义 [Before]、[After]、[Around] 通知方法。
 * 切面类同时也是一个组件，会被自动注册到容器中。
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class Aspect

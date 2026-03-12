package top.wcpe.taboolib.ioc.annotation

/**
 * 前置通知。
 *
 * 在目标方法执行之前调用。通知方法可以无参，也可以接收与目标方法相同的参数。
 *
 * @param value 切点表达式，格式：`execution(类名.方法名)`
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class Before(val value: String)

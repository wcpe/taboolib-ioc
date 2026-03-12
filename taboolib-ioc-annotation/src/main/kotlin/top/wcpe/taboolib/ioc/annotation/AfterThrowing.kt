package top.wcpe.taboolib.ioc.annotation

/**
 * 异常后通知。
 *
 * 在目标方法抛出异常后调用。通知方法可以无参，也可以接收一个 Throwable 参数来获取异常。
 * 异常在通知执行后会被重新抛出。
 *
 * @param value 切点表达式，格式：`execution(类名.方法名)`
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class AfterThrowing(val value: String)

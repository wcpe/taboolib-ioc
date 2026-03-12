package top.wcpe.taboolib.ioc.annotation

/**
 * 返回后通知。
 *
 * 在目标方法正常返回后调用。通知方法可以无参，也可以接收一个参数来获取返回值。
 *
 * @param value 切点表达式，格式：`execution(类名.方法名)`
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class AfterReturning(val value: String)

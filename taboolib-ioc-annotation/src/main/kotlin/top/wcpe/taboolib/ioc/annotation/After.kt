package top.wcpe.taboolib.ioc.annotation

/**
 * 后置通知。
 *
 * 在目标方法执行之后调用（无论是否抛出异常）。通知方法可以无参。
 *
 * @param value 切点表达式，格式：`execution(类名.方法名)`
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class After(val value: String)

package top.wcpe.taboolib.ioc.annotation

/**
 * 环绕通知。
 *
 * 包裹目标方法的执行，通知方法必须接收一个 `MethodInvocation` 参数并调用 `proceed()` 继续执行。
 *
 * @param value 切点表达式，格式：`execution(类名.方法名)`
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class Around(val value: String)

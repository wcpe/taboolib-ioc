package top.wcpe.taboolib.ioc.annotation

/**
 * 切点定义。
 *
 * 用于定义可复用的切点表达式，其他通知注解可以通过方法名引用。
 *
 * @param value 切点表达式
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class Pointcut(val value: String)

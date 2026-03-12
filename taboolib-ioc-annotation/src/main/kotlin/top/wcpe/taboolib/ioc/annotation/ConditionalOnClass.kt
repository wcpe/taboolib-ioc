package top.wcpe.taboolib.ioc.annotation

/**
 * 当指定的类都存在于 ClassPath 中时，Bean 才会被注册。
 *
 * @param value 类全限定名数组
 */
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class ConditionalOnClass(vararg val value: String)

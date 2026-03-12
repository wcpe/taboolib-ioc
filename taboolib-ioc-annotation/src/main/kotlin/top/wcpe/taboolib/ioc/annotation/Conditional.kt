package top.wcpe.taboolib.ioc.annotation

import kotlin.reflect.KClass

/**
 * 通用条件注解。
 *
 * 当所有指定的 [Condition] 实现都返回 true 时，Bean 才会被注册到容器中。
 *
 * @param value 条件实现类数组
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class Conditional(vararg val value: KClass<out Condition>)

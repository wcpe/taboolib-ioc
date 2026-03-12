package top.wcpe.taboolib.ioc.annotation

import kotlin.reflect.KClass

/**
 * 当容器中存在指定类型或名称的 Bean 定义时，Bean 才会被注册。
 *
 * [value] 和 [name] 至少指定一个。多个条件之间为 AND 关系。
 *
 * @param value 需要存在的 Bean 类型
 * @param name 需要存在的 Bean 名称
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class ConditionalOnBean(
    vararg val value: KClass<*> = [],
    val name: Array<String> = []
)

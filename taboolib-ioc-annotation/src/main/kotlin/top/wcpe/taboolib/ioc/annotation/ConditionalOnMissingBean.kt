package top.wcpe.taboolib.ioc.annotation

import kotlin.reflect.KClass

/**
 * 当容器中不存在指定类型或名称的 Bean 定义时，Bean 才会被注册。
 *
 * [value] 和 [name] 至少指定一个。任一条件匹配到已有 Bean 则跳过注册。
 *
 * @param value 不应存在的 Bean 类型
 * @param name 不应存在的 Bean 名称
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class ConditionalOnMissingBean(
    vararg val value: KClass<*> = [],
    val name: Array<String> = []
)

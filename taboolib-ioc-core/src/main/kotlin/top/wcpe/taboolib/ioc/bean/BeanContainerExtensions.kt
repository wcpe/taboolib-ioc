package top.wcpe.taboolib.ioc.bean

/**
 * BeanContainer 的 Kotlin 扩展方法，提供更简洁的 Bean 获取方式。
 */

/**
 * 按类型获取 Bean，找不到时抛出异常。
 *
 * ```kotlin
 * val service = bean<UserService>()
 * val gateway = bean<PaymentGateway>("wechatGateway")
 * ```
 */
inline fun <reified T> bean(name: String? = null): T =
    BeanContainer.getBean(T::class.java, name)
        ?: throw IllegalStateException("Bean not found: ${T::class.java.name}${if (name != null) " (name=$name)" else ""}")

/**
 * 按类型获取 Bean，找不到时返回 null。
 *
 * ```kotlin
 * val service = beanOrNull<UserService>()
 * ```
 */
inline fun <reified T> beanOrNull(name: String? = null): T? =
    BeanContainer.getBean(T::class.java, name)

/**
 * 获取指定类型的所有 Bean。
 *
 * ```kotlin
 * val gateways = beans<PaymentGateway>()
 * ```
 */
inline fun <reified T> beans(): List<T> =
    BeanContainer.getBeansOfType(T::class.java)

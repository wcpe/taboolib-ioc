package top.wcpe.taboolib.ioc.inject

import taboolib.common.platform.function.debug
import taboolib.common.platform.function.warning
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy

/**
 * 延迟注入代理工厂。
 *
 * 为标记了 @Lazy 的注入点创建 JDK 动态代理，在首次方法调用时才真正解析 Bean 实例。
 * 仅支持接口类型；具体类无法创建代理，将回退到立即注入。
 *
 * 代理的 `toString` / `hashCode` / `equals` 基于代理对象自身的身份，且**不随解析状态翻转**
 * （详见 `LazyInvocationHandler` 的 Object 契约说明）。
 */
object LazyProxyFactory {

    /**
     * 判断指定类型是否支持创建延迟代理。
     * 仅接口类型支持 JDK 动态代理。
     */
    fun canProxy(type: Class<*>): Boolean = type.isInterface

    /**
     * 创建延迟代理实例。
     *
     * @param type 注入点的接口类型
     * @param beanResolver 延迟解析 Bean 的函数，首次调用时触发
     * @return 代理实例，首次方法调用时才解析真实 Bean
     * @throws IllegalArgumentException 如果 type 不是接口
     */
    @Suppress("UNCHECKED_CAST")
    fun <T> createProxy(type: Class<T>, beanResolver: () -> Any?): T {
        require(type.isInterface) { "@Lazy 代理仅支持接口类型，${type.name} 不是接口" }

        val handler = LazyInvocationHandler(type, beanResolver)
        return Proxy.newProxyInstance(
            type.classLoader ?: Thread.currentThread().contextClassLoader,
            arrayOf(type),
            handler
        ) as T
    }

    /**
     * 延迟调用处理器。
     * 首次方法调用时解析真实 Bean 并缓存，后续调用直接委托给真实实例。
     *
     * ## Object 契约（C-P2-12/13）
     *
     * `toString` / `hashCode` / `equals` **始终基于代理对象本身**，与是否已解析无关：
     * - `hashCode` = `System.identityHashCode(proxy)`（稳定，不会因解析而翻转）
     * - `equals` = 引用相等（`proxy === other`）
     * - `toString` = `LazyProxy[类型]`，解析后附带真实实例类型，但仍是稳定的代理标识
     *
     * 这样设计的原因：若在解析前后切换语义（解析前用合成值、解析后用 `target` 的值），
     * 把代理作为 HashMap key 时 `hashCode` 会在解析后突变，导致 key 丢失。
     * 因此约定代理的身份就是它自己，这是稳定且可预期的契约。
     */
    private class LazyInvocationHandler(
        private val type: Class<*>,
        private val beanResolver: () -> Any?
    ) : InvocationHandler {

        @Volatile
        private var resolved = false
        private var target: Any? = null

        override fun invoke(proxy: Any, method: Method, args: Array<out Any>?): Any? {
            // Object 契约：稳定、不随解析状态翻转
            when (method.name) {
                "toString" -> return if (resolved) {
                    "LazyProxy[${type.name}](resolved=${target?.javaClass?.name})"
                } else {
                    "LazyProxy[${type.name}](unresolved)"
                }
                "hashCode" -> return System.identityHashCode(proxy)
                "equals" -> return proxy === args?.firstOrNull()
            }

            val instance = resolveTarget()
                ?: throw IllegalStateException("@Lazy 延迟注入失败: 无法解析 ${type.name} 的 Bean 实例")

            return if (args != null) {
                method.invoke(instance, *args)
            } else {
                method.invoke(instance)
            }
        }

        @Synchronized
        private fun resolveTarget(): Any? {
            if (!resolved) {
                target = beanResolver()
                resolved = true
                if (target != null) {
                    debug("[IoC] @Lazy 代理已解析: ${type.name}")
                } else {
                    warning("[IoC] @Lazy 代理解析失败: ${type.name} 未找到 Bean 实例")
                }
            }
            return target
        }
    }
}

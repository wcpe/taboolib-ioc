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
     */
    private class LazyInvocationHandler(
        private val type: Class<*>,
        private val beanResolver: () -> Any?
    ) : InvocationHandler {

        @Volatile
        private var resolved = false
        private var target: Any? = null

        override fun invoke(proxy: Any, method: Method, args: Array<out Any>?): Any? {
            // toString / hashCode / equals 在未解析时提供默认行为
            if (!resolved) {
                when (method.name) {
                    "toString" -> return "LazyProxy[${type.name}](unresolved)"
                    "hashCode" -> return System.identityHashCode(proxy)
                    "equals" -> return proxy === args?.firstOrNull()
                }
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

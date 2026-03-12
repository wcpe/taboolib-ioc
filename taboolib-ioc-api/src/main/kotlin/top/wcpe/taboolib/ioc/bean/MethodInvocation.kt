package top.wcpe.taboolib.ioc.bean

import java.lang.reflect.Method

/**
 * 方法调用上下文，用于 @Around 环绕通知。
 *
 * @property target 目标对象
 * @property method 被调用的方法
 * @property arguments 方法参数
 */
class MethodInvocation(
    val target: Any,
    val method: Method,
    val arguments: Array<out Any?>?,
    private val chain: MethodInvocationChain
) {

    /**
     * 继续执行拦截器链或目标方法。
     */
    fun proceed(): Any? {
        return chain.proceed(this)
    }
}

/**
 * 方法调用链接口，由拦截器链实现。
 */
interface MethodInvocationChain {
    fun proceed(invocation: MethodInvocation): Any?
}

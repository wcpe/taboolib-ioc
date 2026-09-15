package top.wcpe.taboolib.ioc.bean

import java.lang.reflect.Method

/**
 * 方法调用上下文，用于 @Around 环绕通知。
 *
 * @property target 目标对象
 * @property method 被调用的方法
 * @property arguments 方法参数
 *
 * 说明：本类自 1.3.x 起为 `open` 且属性为可写，用于支持容器内部的
 * **零分配复用**（热路径上不再每次新建对象）。对使用者而言是兼容变更
 * （构造签名与字段描述符不变），但你**不应**自己复用/修改这些属性 ——
 * 容器保证在一次 `proceed()` 返回前它们始终是当前调用的值。
 */
open class MethodInvocation(
    var target: Any,
    var method: Method,
    var arguments: Array<out Any?>?,
    private val chain: MethodInvocationChain
) {

    /**
     * 继续执行拦截器链或目标方法。
     */
    open fun proceed(): Any? {
        return chain.proceed(this)
    }
}

/**
 * 方法调用链接口，由拦截器链实现。
 */
interface MethodInvocationChain {
    fun proceed(invocation: MethodInvocation): Any?
}

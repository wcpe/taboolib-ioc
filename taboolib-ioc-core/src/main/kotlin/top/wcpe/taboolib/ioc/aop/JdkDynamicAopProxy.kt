package top.wcpe.taboolib.ioc.aop

import top.wcpe.taboolib.ioc.bean.Advisor
import java.lang.reflect.InvocationHandler
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method

/**
 * JDK 动态代理实现。
 *
 * 对实现了接口的 Bean 创建代理，拦截方法调用并执行匹配的通知。
 */
class JdkDynamicAopProxy(
    private val target: Any,
    private val targetClass: Class<*>,
    private val advisors: List<Advisor>
) : InvocationHandler {

    override fun invoke(proxy: Any, method: Method, args: Array<out Any?>?): Any? {
        // Object 方法直接委托
        if (method.declaringClass == Any::class.java) {
            return method.invoke(target, *(args ?: emptyArray()))
        }

        val matchedAdvisors = advisors.filter { it.matches(targetClass, method) }
        if (matchedAdvisors.isEmpty()) {
            try {
                return method.invoke(target, *(args ?: emptyArray()))
            } catch (e: InvocationTargetException) {
                throw e.targetException
            }
        }

        val chain = InterceptorChain(target, method, args, matchedAdvisors)
        return chain.execute()
    }
}

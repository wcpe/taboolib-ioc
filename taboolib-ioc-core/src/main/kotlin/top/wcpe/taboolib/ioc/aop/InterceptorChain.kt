package top.wcpe.taboolib.ioc.aop

import top.wcpe.taboolib.ioc.bean.AdviceType
import top.wcpe.taboolib.ioc.bean.Advisor
import top.wcpe.taboolib.ioc.bean.MethodInvocation
import top.wcpe.taboolib.ioc.bean.MethodInvocationChain
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method

/**
 * 拦截器链 — 按顺序执行 Before → Around → After 通知。
 */
class InterceptorChain(
    private val target: Any,
    private val method: Method,
    private val args: Array<out Any?>?,
    private val advisors: List<Advisor>
) : MethodInvocationChain {

    private val beforeAdvisors = advisors.filter { it.adviceType == AdviceType.BEFORE }
    private val afterAdvisors = advisors.filter { it.adviceType == AdviceType.AFTER }
    private val aroundAdvisors = advisors.filter { it.adviceType == AdviceType.AROUND }
    private var aroundIndex = 0

    /**
     * 执行拦截器链。
     */
    fun execute(): Any? {
        // 1. 执行所有 @Before 通知
        for (advisor in beforeAdvisors) {
            invokeAdvice(advisor)
        }

        // 2. 执行 @Around 链（或直接调用目标方法）
        val result: Any?
        try {
            result = if (aroundAdvisors.isNotEmpty()) {
                aroundIndex = 0
                val invocation = MethodInvocation(target, method, args, this)
                proceed(invocation)
            } else {
                invokeTarget()
            }
        } finally {
            // 3. 执行所有 @After 通知（无论是否异常）
            for (advisor in afterAdvisors) {
                runCatching { invokeAdvice(advisor) }
            }
        }

        return result
    }

    override fun proceed(invocation: MethodInvocation): Any? {
        return if (aroundIndex < aroundAdvisors.size) {
            val advisor = aroundAdvisors[aroundIndex++]
            advisor.adviceMethod.invoke(advisor.aspectInstance, invocation)
        } else {
            invokeTarget()
        }
    }

    private fun invokeTarget(): Any? {
        try {
            return method.invoke(target, *(args ?: emptyArray()))
        } catch (e: InvocationTargetException) {
            throw e.targetException
        }
    }

    private fun invokeAdvice(advisor: Advisor) {
        if (advisor.adviceMethod.parameterCount == 0) {
            advisor.adviceMethod.invoke(advisor.aspectInstance)
        } else {
            // 尝试传递目标方法参数
            advisor.adviceMethod.invoke(advisor.aspectInstance, *(args ?: emptyArray()))
        }
    }
}

package top.wcpe.taboolib.ioc.aop

import top.wcpe.taboolib.ioc.bean.AdviceType
import top.wcpe.taboolib.ioc.bean.Advisor
import top.wcpe.taboolib.ioc.bean.MethodInvocation
import top.wcpe.taboolib.ioc.bean.MethodInvocationChain
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method

/**
 * 拦截器链 — 按顺序执行 Before → Around → After / AfterReturning / AfterThrowing 通知。
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
    private val afterReturningAdvisors = advisors.filter { it.adviceType == AdviceType.AFTER_RETURNING }
    private val afterThrowingAdvisors = advisors.filter { it.adviceType == AdviceType.AFTER_THROWING }
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
        var result: Any? = null
        var thrown: Throwable? = null
        try {
            result = if (aroundAdvisors.isNotEmpty()) {
                aroundIndex = 0
                val invocation = MethodInvocation(target, method, args, this)
                proceed(invocation)
            } else {
                invokeTarget()
            }
        } catch (e: Throwable) {
            thrown = e
        } finally {
            // 3. 执行所有 @After 通知（无论是否异常）
            for (advisor in afterAdvisors) {
                runCatching { invokeAdvice(advisor) }
            }
        }

        // 4. 根据结果执行 @AfterReturning 或 @AfterThrowing
        if (thrown != null) {
            for (advisor in afterThrowingAdvisors) {
                runCatching { invokeAfterThrowing(advisor, thrown) }
            }
            throw thrown
        } else {
            for (advisor in afterReturningAdvisors) {
                runCatching { invokeAfterReturning(advisor, result) }
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

    /**
     * 调用 @AfterReturning 通知。
     * 无参 = 不关心返回值；1 个参数 = 接收返回值(Any?)。
     */
    private fun invokeAfterReturning(advisor: Advisor, result: Any?) {
        if (advisor.adviceMethod.parameterCount == 0) {
            advisor.adviceMethod.invoke(advisor.aspectInstance)
        } else {
            advisor.adviceMethod.invoke(advisor.aspectInstance, result)
        }
    }

    /**
     * 调用 @AfterThrowing 通知。
     * 无参 = 不关心异常；1 个参数 = 接收异常(Throwable)。
     */
    private fun invokeAfterThrowing(advisor: Advisor, throwable: Throwable) {
        if (advisor.adviceMethod.parameterCount == 0) {
            advisor.adviceMethod.invoke(advisor.aspectInstance)
        } else {
            advisor.adviceMethod.invoke(advisor.aspectInstance, throwable)
        }
    }
}

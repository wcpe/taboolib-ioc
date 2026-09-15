package top.wcpe.taboolib.ioc.aop

import top.wcpe.taboolib.ioc.bean.AdviceType
import top.wcpe.taboolib.ioc.bean.Advisor
import java.lang.reflect.Method

/**
 * [AopPlan] 的构建：把「匹配到的通知器列表」按通知类型分组并预热成调用器。
 *
 * 代理路径与编译期织入路径共用（差异只在目标方法的调用方式）。
 */
internal object AopPlans {

    fun build(method: Method, matched: List<Advisor>, targetInvoker: TargetInvoker): AopPlan {
        val before = ArrayList<AdviceInvoker>(1)
        val around = ArrayList<AdviceInvoker>(1)
        val after = ArrayList<AdviceInvoker>(0)
        val afterReturning = ArrayList<AdviceInvoker>(0)
        val afterThrowing = ArrayList<AdviceInvoker>(0)
        for (advisor in matched) {
            val invoker = AopInvokers.prepareAdvice(advisor)
            when (advisor.adviceType) {
                AdviceType.BEFORE -> before.add(invoker)
                AdviceType.AROUND -> around.add(invoker)
                AdviceType.AFTER -> after.add(invoker)
                AdviceType.AFTER_RETURNING -> afterReturning.add(invoker)
                AdviceType.AFTER_THROWING -> afterThrowing.add(invoker)
            }
        }
        return AopPlan(
            before = before.toTypedArray(),
            around = around.toTypedArray(),
            after = after.toTypedArray(),
            afterReturning = afterReturning.toTypedArray(),
            afterThrowing = afterThrowing.toTypedArray(),
            targetInvoker = targetInvoker,
            method = method
        )
    }
}

package top.wcpe.taboolib.ioc.aop

import top.wcpe.taboolib.ioc.annotation.Order
import top.wcpe.taboolib.ioc.bean.Advisor
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Advisor 注册表 — 管理所有切面通知器。
 */
class AdvisorRegistry {

    private val advisors = CopyOnWriteArrayList<Advisor>()

    fun register(advisor: Advisor) {
        advisors.add(advisor)
    }

    fun registerAll(list: List<Advisor>) {
        advisors.addAll(list)
    }

    /**
     * 查找匹配给定目标类的所有通知器，按切面类的 @Order 值升序排列。
     */
    fun findMatchingAdvisors(targetClass: Class<*>): List<Advisor> {
        return advisors.filter { advisor ->
            targetClass.methods.any { method -> advisor.matches(targetClass, method) }
        }.sortedBy { advisor ->
            advisor.aspectInstance.javaClass.getAnnotation(Order::class.java)?.value ?: Int.MAX_VALUE
        }
    }

    fun getAll(): List<Advisor> = advisors.toList()

    fun clear() {
        advisors.clear()
    }
}

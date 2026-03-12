package top.wcpe.taboolib.ioc.aop

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
     * 查找匹配给定目标类的所有通知器。
     */
    fun findMatchingAdvisors(targetClass: Class<*>): List<Advisor> {
        return advisors.filter { advisor ->
            // 检查目标类的所有方法是否有任一匹配
            targetClass.methods.any { method -> advisor.matches(targetClass, method) }
        }
    }

    fun getAll(): List<Advisor> = advisors.toList()

    fun clear() {
        advisors.clear()
    }
}

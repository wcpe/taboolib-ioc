package top.wcpe.taboolib.ioc.aop

import taboolib.common.platform.function.warning
import top.wcpe.taboolib.ioc.annotation.Order
import top.wcpe.taboolib.ioc.bean.Advisor
import java.lang.reflect.Modifier
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Advisor 注册表 — 管理所有切面通知器。
 */
class AdvisorRegistry {

    private val advisors = CopyOnWriteArrayList<Advisor>()

    /**
     * 已对其输出过「仅匹配 static 方法」告警的 Advisor 身份，避免重复刷屏。
     * 使用 `System.identityHashCode` + Advisor 引用，Advisor 无自定义 equals。
     */
    private val staticOnlyWarned =
        java.util.Collections.newSetFromMap(
            java.util.WeakHashMap<Advisor, Boolean>()
        )

    fun register(advisor: Advisor) {
        advisors.add(advisor)
    }

    fun registerAll(list: List<Advisor>) {
        advisors.addAll(list)
    }

    /**
     * 查找匹配给定目标类的所有通知器，按切面类的 @Order 值升序排列。
     *
     * 注意（A-P1-14）：JDK 动态代理仅承载「接口实例方法」。若某 Advisor 在该类上
     * **只**匹配到 static 方法（而无任何实例方法命中），则该 Advisor 会被「命中」却
     * 在 JDK 代理下永不生效。此时输出 warning 提示（语义与静态引擎侧
     * `aop-static-method-pointcut` 规则一致），但**仍保留匹配** —— 以兼容可能复用
     * 该匹配结果的其他路径（如 CGLIB 或显式反射调用）。
     */
    fun findMatchingAdvisors(targetClass: Class<*>): List<Advisor> {
        return advisors.filter { advisor ->
            var matchedAny = false
            var matchedInstance = false
            for (method in targetClass.methods) {
                if (!advisor.matches(targetClass, method)) continue
                matchedAny = true
                if (!Modifier.isStatic(method.modifiers)) {
                    matchedInstance = true
                    break
                }
            }
            if (matchedAny && !matchedInstance) {
                warnStaticOnly(advisor, targetClass)
            }
            matchedAny
        }.sortedBy { advisor ->
            advisor.aspectInstance.javaClass.getAnnotation(Order::class.java)?.value ?: Int.MAX_VALUE
        }
    }

    /**
     * 输出「仅匹配 static 方法」告警（同源去重，避免每次匹配都刷屏）。
     */
    private fun warnStaticOnly(advisor: Advisor, targetClass: Class<*>) {
        synchronized(staticOnlyWarned) {
            if (!staticOnlyWarned.add(advisor)) return
        }
        warning(
            "[IoC] AOP 切点仅命中 static 方法，JDK 动态代理下不会生效: " +
                "目标类=${targetClass.name}, 切面=${advisor.aspectInstance.javaClass.name}," +
                " 通知方法=${advisor.adviceMethod.name}, 切点=${advisor.pointcut}。" +
                " 原因: JDK 动态代理只代理接口实例方法，static 方法不会被拦截。" +
                " 请将切点指向实例方法，或改用支持类代理的代理方式。"
        )
    }

    fun getAll(): List<Advisor> = advisors.toList()

    fun clear() {
        advisors.clear()
        synchronized(staticOnlyWarned) {
            staticOnlyWarned.clear()
        }
    }
}

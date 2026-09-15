package top.wcpe.taboolib.ioc.aop

import taboolib.common.platform.function.warning
import top.wcpe.taboolib.ioc.annotation.Order
import top.wcpe.taboolib.ioc.bean.Advisor
import java.lang.reflect.Modifier
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Advisor 注册表 — 管理所有切面通知器。
 */
class AdvisorRegistry {

    private val advisors = CopyOnWriteArrayList<Advisor>()

    /**
     * 注册表版本号：每次 [register] / [registerAll] / [clear] 自增。
     *
     * 代理侧的「方法 → 调用计划」缓存以此为失效依据 —— 既让热路径不必每次重做切点匹配，
     * 又保证**运行期新注册切面**（手动注册路径）能立刻对已存在的代理生效。
     */
    @Volatile
    var version: Int = 0
        private set

    /** 类级匹配结果缓存：`Class -> (版本, 匹配到的通知器)`。 */
    private val snapshotCache = ConcurrentHashMap<Class<*>, ClassSnapshot>()

    private class ClassSnapshot(val version: Int, val matched: List<Advisor>)

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
        version++
    }

    fun registerAll(list: List<Advisor>) {
        if (list.isEmpty()) return
        advisors.addAll(list)
        version++
    }

    /**
     * 取「给定目标类的匹配通知器」快照（带版本校验的缓存）。
     *
     * 与 [findMatchingAdvisors] 结果一致，但命中缓存时不做任何遍历 —— 供代理热路径使用。
     */
    fun snapshotFor(targetClass: Class<*>): List<Advisor> {
        val current = version
        val cached = snapshotCache[targetClass]
        if (cached != null && cached.version == current) {
            return cached.matched
        }
        val matched = findMatchingAdvisors(targetClass)
        snapshotCache[targetClass] = ClassSnapshot(current, matched)
        return matched
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
        snapshotCache.clear()
        version++
        synchronized(staticOnlyWarned) {
            staticOnlyWarned.clear()
        }
    }
}

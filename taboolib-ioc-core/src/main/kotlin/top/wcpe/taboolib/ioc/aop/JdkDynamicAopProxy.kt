package top.wcpe.taboolib.ioc.aop

import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap

/**
 * JDK 动态代理的调用处理器（热路径优化版）。
 *
 * ## 与旧实现（每次调用重做一切）的差别
 *
 * | 环节 | 旧 | 新 |
 * |---|---|---|
 * | 切点匹配 | 每次调用 `advisors.filter { it.matches(...) }` | 代理创建期一次，按方法缓存 |
 * | 建链 | 每次 `new InterceptorChain` → 按类型 5 次 `filter` | 一次构建 [AopPlan] 并按方法缓存 |
 * | 参数拷贝 | 每次 `Arrays.copyOf` | 直接透传代理给到的数组 |
 * | `MethodInvocation` | 每次 new | 线程局部池化复用（[ReusableInvocation]） |
 * | 反射 | 通知 + 目标各一次 `Method.invoke` | 预热的 `MethodHandle`（无法 unreflect 时自动回退反射） |
 *
 * 之所以能缓存「方法 → 计划」，是因为切面注册表带**版本号**：运行期新注册切面（手动注册路径）
 * 会让版本自增，代理随即清空缓存并重建，语义与旧实现一致。
 */
class JdkDynamicAopProxy(
    private val target: Any,
    private val targetClass: Class<*>,
    private val advisorRegistry: AdvisorRegistry
) : InvocationHandler {

    /** 方法 → [AopPlan]；无通知命中时存 [NO_PLAN] 占位。 */
    private val plans = ConcurrentHashMap<Method, Any>()

    /** 无通知命中方法的预热调用器。 */
    private val directInvokers = ConcurrentHashMap<Method, TargetInvoker>()

    @Volatile
    private var planVersion: Int = -1

    override fun invoke(proxy: Any, method: Method, args: Array<out Any?>?): Any? {
        // Object 方法（equals/hashCode/toString）不参与切面，直接落到原始实例
        if (method.declaringClass == Any::class.java) {
            return directInvoker(method).invoke(target, args)
        }

        val currentVersion = advisorRegistry.version
        if (currentVersion != planVersion) {
            synchronized(this) {
                if (currentVersion != planVersion) {
                    plans.clear()
                    planVersion = currentVersion
                }
            }
        }

        val cached = plans[method]
        if (cached != null) {
            return if (cached === NO_PLAN) {
                directInvoker(method).invoke(target, args)
            } else {
                (cached as AopPlan).execute(target, args)
            }
        }

        val matched = if (AopExclusions.isMethodExcluded(targetClass, method)) {
            emptyList()
        } else {
            advisorRegistry.snapshotFor(targetClass)
                .filter { it.matches(targetClass, method) }
        }
        if (matched.isEmpty()) {
            plans[method] = NO_PLAN
            return directInvoker(method).invoke(target, args)
        }
        val plan = AopPlans.build(method, matched, directInvoker(method))
        plans[method] = plan
        return plan.execute(target, args)
    }

    private fun directInvoker(method: Method): TargetInvoker =
        directInvokers.computeIfAbsent(method) { AopInvokers.prepareTarget(target, it) }

    private companion object {
        /** 「该方法没有命中任何通知」的占位标记。 */
        private val NO_PLAN = Any()
    }
}

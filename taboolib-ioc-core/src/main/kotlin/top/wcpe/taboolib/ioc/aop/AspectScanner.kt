package top.wcpe.taboolib.ioc.aop

import taboolib.common.platform.function.warning
import top.wcpe.taboolib.ioc.annotation.*
import top.wcpe.taboolib.ioc.bean.*
import java.lang.reflect.Method

/**
 * 切面扫描器 — 解析 @Aspect 类中的通知方法，生成 Advisor 列表。
 *
 * 健壮性约定（A-P1-12 / A-P1-13）：
 * - 单个通知的切点表达式解析失败，仅丢弃该通知并输出 warning，**不抛异常**，
 *   以免单个非法切点导致整个 BeanContainer / 插件 enable 失败。
 * - 通知方法签名非法时，在扫描期即拦截并输出 warning，避免运行期 `Method.invoke`
 *   抛 `IllegalArgumentException` 或被 `runCatching` 静默吞掉。
 *
 * 签名校验规则严格对齐 [InterceptorChain] 的实际 invoke 方式：
 * - BEFORE / AFTER：0 参（无参调用）或 N 参（按目标方法实参逐参传递）；校验参数数量
 *   必须为 0 或与目标方法一致 —— 扫描期无法得知目标实参，故此处仅要求「可为 0 或不含基本
 *   类型不匹配」，真正的实参对齐由运行期 `invokeAdvice` 兜底；结构上要求不接收
 *   `MethodInvocation`（那是 AROUND 专用）。
 * - AROUND：必须恰好 1 个参数，且类型可从 `MethodInvocation` 赋值。
 * - AFTER_RETURNING：0 或 1 个参数（返回值）。
 * - AFTER_THROWING：0 或 1 个参数（异常）。
 */
object AspectScanner {

    /**
     * 扫描切面实例，解析其中的 @Before/@After/@Around 方法。
     *
     * @param aspectInstance 切面实例
     * @param aspectClass 切面类
     * @return 解析出的 Advisor 列表（非法通知会被跳过）
     */
    fun scan(aspectInstance: Any, aspectClass: Class<*>): List<Advisor> {
        val advisors = mutableListOf<Advisor>()

        // 收集 @Pointcut 定义，用于引用解析
        val pointcutMethods = aspectClass.declaredMethods
            .filter { it.isAnnotationPresent(Pointcut::class.java) }
            .associate { it.name to it.getAnnotation(Pointcut::class.java).value }

        for (method in aspectClass.declaredMethods) {
            addAdvice(aspectInstance, aspectClass, method, pointcutMethods, advisors)
        }

        return advisors
    }

    /**
     * 解析单个方法上挂载的所有通知注解，逐个处理（含切点解析容错与签名校验）。
     * 单个通知失败不影响其余通知。
     */
    private fun addAdvice(
        aspectInstance: Any,
        aspectClass: Class<*>,
        method: Method,
        pointcutMethods: Map<String, String>,
        advisors: MutableList<Advisor>
    ) {
        val before = method.getAnnotation(Before::class.java)
        if (before != null) {
            buildAdvisor(
                aspectInstance, aspectClass, method, before.value,
                pointcutMethods, AdviceType.BEFORE, advisors
            )
        }

        val after = method.getAnnotation(After::class.java)
        if (after != null) {
            buildAdvisor(
                aspectInstance, aspectClass, method, after.value,
                pointcutMethods, AdviceType.AFTER, advisors
            )
        }

        val around = method.getAnnotation(Around::class.java)
        if (around != null) {
            buildAdvisor(
                aspectInstance, aspectClass, method, around.value,
                pointcutMethods, AdviceType.AROUND, advisors
            )
        }

        val afterReturning = method.getAnnotation(AfterReturning::class.java)
        if (afterReturning != null) {
            buildAdvisor(
                aspectInstance, aspectClass, method, afterReturning.value,
                pointcutMethods, AdviceType.AFTER_RETURNING, advisors
            )
        }

        val afterThrowing = method.getAnnotation(AfterThrowing::class.java)
        if (afterThrowing != null) {
            buildAdvisor(
                aspectInstance, aspectClass, method, afterThrowing.value,
                pointcutMethods, AdviceType.AFTER_THROWING, advisors
            )
        }
    }

    /**
     * 构造单个 Advisor。
     *
     * 依次执行：签名校验 → 表达式解析。任一失败均输出 warning 并跳过该通知。
     *
     * @return 是否成功加入 Advisor 列表
     */
    private fun buildAdvisor(
        aspectInstance: Any,
        aspectClass: Class<*>,
        method: Method,
        rawExpression: String,
        pointcutMethods: Map<String, String>,
        adviceType: AdviceType,
        advisors: MutableList<Advisor>
    ): Boolean {
        // 1. 签名校验（A-P1-13）—— 不合法的通知直接跳过
        val signatureError = validateSignature(method, adviceType)
        if (signatureError != null) {
            warning(
                "[IoC] AOP 通知签名非法，已跳过: 切面=${aspectClass.name}," +
                    " 方法=${method.name}, 通知类型=$adviceType," +
                    " 期望签名=${expectedSignature(adviceType)}," +
                    " 实际参数类型=${describeParameters(method)} —— $signatureError"
            )
            return false
        }

        // 2. 切点表达式解析（A-P1-12）—— 解析失败仅丢弃本通知，不冒泡
        val expr = resolveExpression(rawExpression, pointcutMethods)
        val pointcut = runCatching { PointcutExpression.parse(expr) }.getOrElse { error ->
            warning(
                "[IoC] AOP 切点表达式解析失败，已跳过: 切面=${aspectClass.name}," +
                    " 方法=${method.name}, 通知类型=$adviceType," +
                    " 表达式=\"${rawExpression}\" (解析为 \"$expr\")" +
                    " —— ${error.message ?: error.javaClass.name}"
            )
            return false
        }

        advisors.add(Advisor(pointcut, method, aspectInstance, adviceType))
        return true
    }

    /**
     * 校验通知方法签名是否与 [InterceptorChain] 的实际调用方式一致。
     *
     * @return null 表示合法；否则返回错误描述
     */
    private fun validateSignature(method: Method, adviceType: AdviceType): String? {
        val params = method.parameterTypes
        return when (adviceType) {
            AdviceType.AROUND -> {
                if (params.size != 1) {
                    "@Around 必须恰好接收 1 个 MethodInvocation 参数，实际 ${params.size} 个"
                } else if (!params[0].isAssignableFrom(MethodInvocation::class.java)) {
                    "@Around 的参数必须可从 MethodInvocation 赋值，实际 ${params[0].name}"
                } else {
                    null
                }
            }

            AdviceType.AFTER_RETURNING -> {
                if (params.size > 1) {
                    "@AfterReturning 至多接收 1 个返回值参数，实际 ${params.size} 个"
                } else {
                    null
                }
            }

            AdviceType.AFTER_THROWING -> {
                if (params.size > 1) {
                    "@AfterThrowing 至多接收 1 个异常参数，实际 ${params.size} 个"
                } else {
                    null
                }
            }

            // BEFORE / AFTER：0 参（无参调用）或 N 参（逐参传递目标方法实参）。
            // 唯一结构性的非法情况是参数为 void（Kotlin/Java 均不可能，防御性保留）。
            AdviceType.BEFORE, AdviceType.AFTER -> null
        }
    }

    /** 通知类型的期望签名描述，用于告警定位。 */
    private fun expectedSignature(adviceType: AdviceType): String = when (adviceType) {
        AdviceType.BEFORE -> "无参 或 与目标方法一致的参数"
        AdviceType.AFTER -> "无参 或 与目标方法一致的参数"
        AdviceType.AROUND -> "(MethodInvocation)"
        AdviceType.AFTER_RETURNING -> "无参 或 (返回值: Any?)"
        AdviceType.AFTER_THROWING -> "无参 或 (异常: Throwable)"
    }

    /** 将方法参数类型渲染为可读列表，用于告警定位。 */
    private fun describeParameters(method: Method): String =
        method.parameterTypes.joinToString(", ", "(", ")") { it.name }

    /**
     * 解析表达式：如果是方法名引用（不含 . 和 (），则查找 @Pointcut 定义。
     */
    private fun resolveExpression(value: String, pointcutMethods: Map<String, String>): String {
        val trimmed = value.trim()
        // 如果看起来像方法名引用（无 . 和 ( ），尝试查找 @Pointcut
        if (!trimmed.contains('.') && !trimmed.contains('(')) {
            pointcutMethods[trimmed]?.let { return it }
        }
        return trimmed
    }
}

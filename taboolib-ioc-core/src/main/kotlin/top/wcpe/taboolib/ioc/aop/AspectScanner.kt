package top.wcpe.taboolib.ioc.aop

import top.wcpe.taboolib.ioc.annotation.*
import top.wcpe.taboolib.ioc.bean.*

/**
 * 切面扫描器 — 解析 @Aspect 类中的通知方法，生成 Advisor 列表。
 */
object AspectScanner {

    /**
     * 扫描切面实例，解析其中的 @Before/@After/@Around 方法。
     *
     * @param aspectInstance 切面实例
     * @param aspectClass 切面类
     * @return 解析出的 Advisor 列表
     */
    fun scan(aspectInstance: Any, aspectClass: Class<*>): List<Advisor> {
        val advisors = mutableListOf<Advisor>()

        // 收集 @Pointcut 定义，用于引用解析
        val pointcutMethods = aspectClass.declaredMethods
            .filter { it.isAnnotationPresent(Pointcut::class.java) }
            .associate { it.name to it.getAnnotation(Pointcut::class.java).value }

        for (method in aspectClass.declaredMethods) {
            val before = method.getAnnotation(Before::class.java)
            if (before != null) {
                val expr = resolveExpression(before.value, pointcutMethods)
                advisors.add(Advisor(PointcutExpression.parse(expr), method, aspectInstance, AdviceType.BEFORE))
            }

            val after = method.getAnnotation(After::class.java)
            if (after != null) {
                val expr = resolveExpression(after.value, pointcutMethods)
                advisors.add(Advisor(PointcutExpression.parse(expr), method, aspectInstance, AdviceType.AFTER))
            }

            val around = method.getAnnotation(Around::class.java)
            if (around != null) {
                val expr = resolveExpression(around.value, pointcutMethods)
                advisors.add(Advisor(PointcutExpression.parse(expr), method, aspectInstance, AdviceType.AROUND))
            }

            val afterReturning = method.getAnnotation(AfterReturning::class.java)
            if (afterReturning != null) {
                val expr = resolveExpression(afterReturning.value, pointcutMethods)
                advisors.add(Advisor(PointcutExpression.parse(expr), method, aspectInstance, AdviceType.AFTER_RETURNING))
            }

            val afterThrowing = method.getAnnotation(AfterThrowing::class.java)
            if (afterThrowing != null) {
                val expr = resolveExpression(afterThrowing.value, pointcutMethods)
                advisors.add(Advisor(PointcutExpression.parse(expr), method, aspectInstance, AdviceType.AFTER_THROWING))
            }
        }

        return advisors
    }

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

package top.wcpe.taboolib.ioc.bean

import java.lang.reflect.Method

/**
 * 通知类型。
 */
enum class AdviceType {
    BEFORE, AFTER, AROUND, AFTER_RETURNING, AFTER_THROWING
}

/**
 * 通知器 — 将切点表达式与通知方法绑定。
 *
 * @property pointcut 切点表达式
 * @property adviceMethod 通知方法（切面类中的方法）
 * @property aspectInstance 切面实例
 * @property adviceType 通知类型
 */
class Advisor(
    val pointcut: PointcutExpression,
    val adviceMethod: Method,
    val aspectInstance: Any,
    val adviceType: AdviceType
) {

    init {
        adviceMethod.isAccessible = true
    }

    /**
     * 判断此通知器是否匹配给定的目标类和方法。
     */
    fun matches(targetClass: Class<*>, method: Method): Boolean {
        return pointcut.matches(targetClass, method)
    }
}

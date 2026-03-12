package top.wcpe.taboolib.ioc.condition

import top.wcpe.taboolib.ioc.annotation.ConditionContext
import top.wcpe.taboolib.ioc.annotation.ConditionalOnMissingClass

/**
 * [ConditionalOnMissingClass] 条件实现。
 *
 * 检查指定的类是否都不存在于 ClassPath 中。
 */
object OnMissingClassCondition {

    fun matches(annotation: ConditionalOnMissingClass, context: ConditionContext): Boolean {
        val classLoader = context.getClassLoader()
        return annotation.value.all { className ->
            try {
                Class.forName(className, false, classLoader)
                false
            } catch (e: ClassNotFoundException) {
                true
            }
        }
    }
}

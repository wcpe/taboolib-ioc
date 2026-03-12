package top.wcpe.taboolib.ioc.condition

import top.wcpe.taboolib.ioc.annotation.ConditionContext
import top.wcpe.taboolib.ioc.annotation.ConditionalOnClass

/**
 * [ConditionalOnClass] 条件实现。
 *
 * 检查指定的类是否都存在于 ClassPath 中。
 */
object OnClassCondition {

    fun matches(annotation: ConditionalOnClass, context: ConditionContext): Boolean {
        val classLoader = context.getClassLoader()
        return annotation.value.all { className ->
            try {
                Class.forName(className, false, classLoader)
                true
            } catch (e: ClassNotFoundException) {
                false
            }
        }
    }
}

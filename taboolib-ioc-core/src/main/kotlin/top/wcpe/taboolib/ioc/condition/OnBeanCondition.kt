package top.wcpe.taboolib.ioc.condition

import top.wcpe.taboolib.ioc.annotation.ConditionContext
import top.wcpe.taboolib.ioc.annotation.ConditionalOnBean

/**
 * [ConditionalOnBean] 条件实现。
 *
 * 检查容器中是否存在指定类型或名称的 Bean 定义。
 * 类型和名称条件之间为 AND 关系。
 */
object OnBeanCondition {

    fun matches(annotation: ConditionalOnBean, context: ConditionContext): Boolean {
        // 按类型检查：所有指定类型都必须有对应的 Bean 定义
        for (type in annotation.value) {
            if (context.getBeanNamesForType(type.java).isEmpty()) {
                return false
            }
        }
        // 按名称检查：所有指定名称都必须存在
        for (name in annotation.name) {
            if (!context.containsBeanDefinition(name)) {
                return false
            }
        }
        return true
    }
}

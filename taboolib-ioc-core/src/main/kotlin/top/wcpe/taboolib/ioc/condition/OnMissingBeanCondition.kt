package top.wcpe.taboolib.ioc.condition

import top.wcpe.taboolib.ioc.annotation.ConditionContext
import top.wcpe.taboolib.ioc.annotation.ConditionalOnMissingBean

/**
 * [ConditionalOnMissingBean] 条件实现。
 *
 * 检查容器中是否不存在指定类型或名称的 Bean 定义。
 * 任一条件匹配到已有 Bean 则返回 false。
 */
object OnMissingBeanCondition {

    fun matches(annotation: ConditionalOnMissingBean, context: ConditionContext): Boolean {
        // 按类型检查：所有指定类型都不应有对应的 Bean 定义
        for (type in annotation.value) {
            if (context.getBeanNamesForType(type.java).isNotEmpty()) {
                return false
            }
        }
        // 按名称检查：所有指定名称都不应存在
        for (name in annotation.name) {
            if (context.containsBeanDefinition(name)) {
                return false
            }
        }
        return true
    }
}

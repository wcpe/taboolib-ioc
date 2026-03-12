package top.wcpe.taboolib.ioc.annotation

/**
 * 条件接口。
 *
 * 实现此接口以定义自定义条件逻辑，配合 [Conditional] 注解使用。
 */
interface Condition {

    /**
     * 判断条件是否满足
     *
     * @param context 条件评估上下文
     * @return true 表示条件满足，Bean 应被注册
     */
    fun matches(context: ConditionContext): Boolean
}

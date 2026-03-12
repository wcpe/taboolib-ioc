package top.wcpe.taboolib.ioc.condition

import top.wcpe.taboolib.ioc.annotation.*
import java.lang.reflect.AnnotatedElement

/**
 * 条件评估器。
 *
 * 解析类上的条件注解并判断是否应跳过注册。
 * 支持两阶段评估：
 * - 阶段一（扫描时）：评估 @Conditional、@ConditionalOnClass、@ConditionalOnMissingClass、@ConditionalOnProperty
 * - 阶段二（注册后）：评估 @ConditionalOnBean、@ConditionalOnMissingBean
 *
 * 同时支持对 @Bean 方法级别的条件注解评估。
 */
object ConditionEvaluator {

    /**
     * 检查类是否带有需要延迟到阶段二评估的 Bean 条件注解。
     */
    fun hasBeanCondition(clazz: Class<*>): Boolean {
        return clazz.isAnnotationPresent(ConditionalOnBean::class.java) ||
            clazz.isAnnotationPresent(ConditionalOnMissingBean::class.java)
    }

    /**
     * 阶段一评估：检查类级别条件（不依赖 Bean 注册表）。
     *
     * @return true 表示应跳过注册
     */
    fun shouldSkipOnScan(clazz: Class<*>, context: ConditionContext): Boolean {
        return shouldSkipOnScan(clazz as AnnotatedElement, context)
    }

    /**
     * 阶段一评估：检查 AnnotatedElement（Class 或 Method）上的条件注解。
     *
     * @return true 表示应跳过注册
     */
    fun shouldSkipOnScan(element: AnnotatedElement, context: ConditionContext): Boolean {
        // 1. @Conditional — 自定义条件
        val conditional = element.getAnnotation(Conditional::class.java)
        if (conditional != null) {
            for (conditionClass in conditional.value) {
                val condition = conditionClass.java.getDeclaredConstructor().newInstance()
                if (!condition.matches(context)) return true
            }
        }

        // 2. @ConditionalOnClass
        val onClass = element.getAnnotation(ConditionalOnClass::class.java)
        if (onClass != null && !OnClassCondition.matches(onClass, context)) {
            return true
        }

        // 3. @ConditionalOnMissingClass
        val onMissingClass = element.getAnnotation(ConditionalOnMissingClass::class.java)
        if (onMissingClass != null && !OnMissingClassCondition.matches(onMissingClass, context)) {
            return true
        }

        // 4. @ConditionalOnProperty
        val onProperty = element.getAnnotation(ConditionalOnProperty::class.java)
        if (onProperty != null && !OnPropertyCondition.matches(onProperty)) {
            return true
        }

        return false
    }

    /**
     * 阶段二评估：检查 Bean 依赖条件（依赖 Bean 注册表）。
     *
     * @return true 表示应跳过注册
     */
    fun shouldSkipOnBeanCondition(clazz: Class<*>, context: ConditionContext): Boolean {
        return shouldSkipOnBeanCondition(clazz as AnnotatedElement, context)
    }

    /**
     * 阶段二评估：检查 AnnotatedElement（Class 或 Method）上的 Bean 条件注解。
     *
     * @return true 表示应跳过注册
     */
    fun shouldSkipOnBeanCondition(element: AnnotatedElement, context: ConditionContext): Boolean {
        // @ConditionalOnBean
        val onBean = element.getAnnotation(ConditionalOnBean::class.java)
        if (onBean != null && !OnBeanCondition.matches(onBean, context)) {
            return true
        }

        // @ConditionalOnMissingBean
        val onMissingBean = element.getAnnotation(ConditionalOnMissingBean::class.java)
        if (onMissingBean != null && !OnMissingBeanCondition.matches(onMissingBean, context)) {
            return true
        }

        return false
    }

    /**
     * 检查 AnnotatedElement 是否带有需要延迟到阶段二评估的 Bean 条件注解。
     */
    fun hasBeanCondition(element: AnnotatedElement): Boolean {
        return element.isAnnotationPresent(ConditionalOnBean::class.java) ||
            element.isAnnotationPresent(ConditionalOnMissingBean::class.java)
    }
}

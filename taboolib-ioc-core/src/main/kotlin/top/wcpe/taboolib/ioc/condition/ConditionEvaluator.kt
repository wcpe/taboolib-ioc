package top.wcpe.taboolib.ioc.condition

import top.wcpe.taboolib.ioc.annotation.*
import java.lang.reflect.AnnotatedElement
import java.util.concurrent.ConcurrentHashMap

/**
 * 条件评估器。
 *
 * 解析类上的条件注解并判断是否应跳过注册。
 * 支持两阶段评估：
 * - 阶段一（扫描时）：评估 @Conditional、@ConditionalOnClass、@ConditionalOnMissingClass、@ConditionalOnProperty
 * - 阶段二（注册后）：评估 @ConditionalOnBean、@ConditionalOnMissingBean
 *
 * 同时支持对 @Bean 方法级别的条件注解评估。
 *
 * ## A-P1-09：条件类实例缓存
 *
 * 自定义 `@Conditional` 条件类原先每次评估都 `newInstance()`，而两阶段扫描会对同一元素
 * 重复评估，导致条件类被反复实例化、`matches()` 反复调用。这里对每个条件类做单例缓存：
 * 同一 JVM 条件下，一个 `Condition` 实现类只实例化一次，降低启动开销并保持无状态条件语义。
 * （`Condition` 契约要求实现无状态；此处缓存不改变 matches 的结果语义。）
 */
object ConditionEvaluator {

    /** 条件类 -> 单例实例 缓存（A-P1-09）。 */
    private val conditionInstances = ConcurrentHashMap<Class<out Condition>, Condition>()

    /**
     * 获取（或创建）条件类实例。同一条件类在整个 JVM 生命周期内只实例化一次。
     */
    private fun conditionInstance(conditionClass: Class<out Condition>): Condition {
        return conditionInstances.computeIfAbsent(conditionClass) { clazz ->
            clazz.getDeclaredConstructor().newInstance()
        }
    }

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
        // 1. @Conditional — 自定义条件（条件类实例被缓存，避免重复实例化）
        val conditional = element.getAnnotation(Conditional::class.java)
        if (conditional != null) {
            for (conditionClass in conditional.value) {
                val condition = conditionInstance(conditionClass.java)
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

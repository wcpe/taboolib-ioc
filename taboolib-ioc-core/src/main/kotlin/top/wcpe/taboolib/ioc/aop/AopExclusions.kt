package top.wcpe.taboolib.ioc.aop

import top.wcpe.taboolib.ioc.annotation.NoAspect
import java.lang.reflect.Method

/**
 * `@NoAspect` 排除判定的**单一实现**：代理路径与编译期织入路径必须用同一套判定，
 * 否则会出现「代理不切、织入照切」这类不一致。
 */
internal object AopExclusions {

    /** 类级排除：整类不参与 AOP。 */
    fun isClassExcluded(targetClass: Class<*>): Boolean =
        targetClass.isAnnotationPresent(NoAspect::class.java)

    /**
     * 方法级排除。
     *
     * 代理拿到的是**接口方法**，而使用者通常把注解写在**实现类**的方法上，
     * 因此两边都要看（实现方法按名称 + 参数类型在 [targetClass] 上查找）。
     */
    fun isMethodExcluded(targetClass: Class<*>, method: Method): Boolean {
        if (method.isAnnotationPresent(NoAspect::class.java)) return true
        if (method.declaringClass == targetClass) return false
        return try {
            targetClass.getMethod(method.name, *method.parameterTypes)
                .isAnnotationPresent(NoAspect::class.java)
        } catch (e: NoSuchMethodException) {
            false
        }
    }
}

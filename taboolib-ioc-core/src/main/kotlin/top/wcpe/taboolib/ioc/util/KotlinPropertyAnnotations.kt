package top.wcpe.taboolib.ioc.util

import java.lang.reflect.Field
import java.lang.reflect.Method

/**
 * 支持 Kotlin 属性注解。
 *
 * 普通的 `@Inject lateinit var foo` 可能不会直接落在 Java Field 上，
 * 而是出现在编译器生成的 `getFoo$annotations()` 方法上。
 */
object KotlinPropertyAnnotations {

    fun Field.hasAnnotation(annotationClass: Class<out Annotation>): Boolean {
        return getAnnotation(annotationClass) != null ||
            findAnnotationCarrier()?.isAnnotationPresent(annotationClass) == true
    }

    fun <T : Annotation> Field.findAnnotation(annotationClass: Class<T>): T? {
        return getAnnotation(annotationClass) ?: findAnnotationCarrier()?.getAnnotation(annotationClass)
    }

    private fun Field.findAnnotationCarrier(): Method? {
        val capitalized = name.replaceFirstChar { it.uppercase() }
        val names = linkedSetOf(
            "${name}\$annotations",
            "get${capitalized}\$annotations",
            "is${capitalized}\$annotations"
        )
        return declaringClass.declaredMethods.firstOrNull { method ->
            method.parameterCount == 0 && method.name in names
        }
    }
}

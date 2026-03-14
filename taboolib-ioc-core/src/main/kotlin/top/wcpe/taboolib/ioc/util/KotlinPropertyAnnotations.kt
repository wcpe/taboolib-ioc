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
        // 先在声明类（普通 object 或普通类）上查找
        declaringClass.declaredMethods.firstOrNull { method ->
            method.parameterCount == 0 && method.name in names
        }?.let { return it }
        // 若声明类是外部类且有 companion object，在 companion class 上查找
        // （Kotlin companion object 属性的 backing field 在外部类，但 $annotations 方法在 Companion 类）
        return findAnnotationCarrierInCompanion(names)
    }

    private fun Field.findAnnotationCarrierInCompanion(names: Set<String>): Method? {
        return try {
            val companionField = declaringClass.getDeclaredField("Companion")
            val companionClass = companionField.type
            companionClass.declaredMethods.firstOrNull { method ->
                method.parameterCount == 0 && method.name in names
            }
        } catch (e: NoSuchFieldException) {
            null
        } catch (e: NoClassDefFoundError) {
            null
        }
    }
}

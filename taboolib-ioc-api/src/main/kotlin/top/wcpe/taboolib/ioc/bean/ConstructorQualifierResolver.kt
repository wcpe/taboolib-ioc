package top.wcpe.taboolib.ioc.bean

import top.wcpe.taboolib.ioc.annotation.Named
import java.lang.reflect.Constructor

object ConstructorQualifierResolver {

    fun resolve(constructor: Constructor<*>, index: Int): String? {
        val parameterNamed = constructor.parameterAnnotations[index]
            .filterIsInstance<Named>()
            .firstOrNull()
            ?.value
            ?.takeIf { it.isNotEmpty() }
        if (parameterNamed != null) {
            return parameterNamed
        }

        val parameter = constructor.parameters[index]
        val fieldByName = parameter.name
            ?.takeIf { !it.startsWith("arg") }
            ?.let { fieldName ->
                constructor.declaringClass.declaredFields.firstOrNull { field ->
                    field.name == fieldName && field.type == parameter.type
                }
            }

        val fieldNamed = fieldByName?.getAnnotation(Named::class.java)
            ?.value
            ?.takeIf { it.isNotEmpty() }
        if (fieldNamed != null) {
            return fieldNamed
        }

        val annotatedFields = constructor.declaringClass.declaredFields.filter { field ->
            field.type == parameter.type && field.isAnnotationPresent(Named::class.java)
        }
        return annotatedFields.singleOrNull()
            ?.getAnnotation(Named::class.java)
            ?.value
            ?.takeIf { it.isNotEmpty() }
    }
}
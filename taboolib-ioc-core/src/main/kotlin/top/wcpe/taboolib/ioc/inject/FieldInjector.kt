package top.wcpe.taboolib.ioc.inject

import top.wcpe.taboolib.ioc.bean.BeanDefinition
import top.wcpe.taboolib.ioc.bean.BeanRegistry

/**
 * 字段注入器 - 处理字段和方法的依赖注入
 */
class FieldInjector(
    private val registry: BeanRegistry,
    private val beanProvider: (type: Class<*>, name: String?) -> Any?
) {

    /**
     * 注入字段依赖
     */
    fun injectFields(instance: Any, definition: BeanDefinition) {
        for (injectField in definition.injectFields) {
            val value = resolveDependency(
                injectField.requiredType,
                injectField.nameQualifier
            )
            if (value != null) {
                injectField.field.isAccessible = true
                injectField.field.set(instance, value)
            }
        }
    }

    /**
     * 注入方法依赖（setter 注入）
     */
    fun injectMethods(instance: Any, definition: BeanDefinition) {
        for (injectMethod in definition.injectMethods) {
            val args = injectMethod.parameters.map { param ->
                resolveDependency(param.type, param.nameQualifier)
            }.toTypedArray()

            injectMethod.method.isAccessible = true
            injectMethod.method.invoke(instance, *args)
        }
    }

    /**
     * 解析依赖值
     */
    private fun resolveDependency(type: Class<*>, nameQualifier: String?): Any? {
        // 如果有名称限定，按名称查找
        if (!nameQualifier.isNullOrEmpty()) {
            val definition = registry.getByName(nameQualifier)
            if (definition != null && type.isAssignableFrom(definition.type)) {
                return beanProvider(type, nameQualifier)
            }
            return null
        }

        // 按类型查找
        val definitions = registry.getByType(type)
        return when {
            definitions.isEmpty() -> null
            definitions.size == 1 -> beanProvider(type, null)
            else -> beanProvider(type, definitions.first().name)
        }
    }
}

package top.wcpe.taboolib.ioc.inject

import taboolib.common.platform.function.debug
import taboolib.common.platform.function.warning
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
            if (injectField.lazy) {
                // 延迟注入：创建代理或跳过
                injectLazyField(instance, injectField.requiredType, injectField.nameQualifier, injectField.field)
            } else {
                val value = resolveDependency(
                    injectField.requiredType,
                    injectField.nameQualifier
                )
                if (value != null) {
                    injectField.field.isAccessible = true
                    injectField.field.set(instance, value)
                } else if (injectField.required) {
                    throw IllegalStateException(
                        "[IoC] 必需的字段注入失败: ${instance.javaClass.simpleName}.${injectField.field.name}" +
                            " (类型=${injectField.requiredType.simpleName}" +
                            "${if (injectField.nameQualifier != null) ", 名称=${injectField.nameQualifier}" else ""})" +
                            " — 未找到匹配的 Bean。如果该依赖是可选的，请使用 @Inject(required=false)"
                    )
                } else {
                    warning(
                        "[IoC] 可选字段注入未找到匹配 Bean: ${instance.javaClass.simpleName}.${injectField.field.name}" +
                            " (类型=${injectField.requiredType.simpleName}" +
                            "${if (injectField.nameQualifier != null) ", 名称=${injectField.nameQualifier}" else ""})"
                    )
                }
            }
        }
    }

    /**
     * 为 object 类注入单个延迟字段（供 ObjectInjector 调用）
     */
    fun injectLazyField(instance: Any, type: Class<*>, nameQualifier: String?, field: java.lang.reflect.Field) {
        if (!LazyProxyFactory.canProxy(type)) {
            warning("[IoC] @Lazy 仅支持接口类型，${type.name} 不是接口，回退到立即注入")
            val value = resolveDependency(type, nameQualifier)
            if (value != null) {
                field.isAccessible = true
                field.set(instance, value)
            }
            return
        }

        val proxy = LazyProxyFactory.createProxy(type) {
            resolveDependency(type, nameQualifier)
        }
        field.isAccessible = true
        field.set(instance, proxy)
        debug("[IoC] @Lazy 代理已注入字段: ${instance.javaClass.simpleName}.${field.name}")
    }

    /**
     * 解析延迟代理（供构造函数参数使用）
     */
    fun resolveLazyDependency(type: Class<*>, nameQualifier: String?): Any? {
        if (!LazyProxyFactory.canProxy(type)) {
            warning("[IoC] @Lazy 仅支持接口类型，${type.name} 不是接口，回退到立即解析")
            return resolveDependency(type, nameQualifier)
        }

        return LazyProxyFactory.createProxy(type) {
            resolveDependency(type, nameQualifier)
        }
    }

    /**
     * 注入方法依赖（setter 注入）
     */
    fun injectMethods(instance: Any, definition: BeanDefinition) {
        for (injectMethod in definition.injectMethods) {
            val args = injectMethod.parameters.mapIndexed { index, param ->
                val value = resolveDependency(param.type, param.nameQualifier)
                if (value == null) {
                    throw IllegalStateException(
                        "[IoC] 方法注入参数解析失败: ${instance.javaClass.simpleName}.${injectMethod.method.name}()" +
                            " 参数[$index] (类型=${param.type.simpleName}" +
                            "${if (param.nameQualifier != null) ", 名称=${param.nameQualifier}" else ""})" +
                            " — 未找到匹配的 Bean。请确保依赖已注册或使用 @Named 指定正确的 Bean 名称"
                    )
                }
                value
            }.toTypedArray()

            injectMethod.method.isAccessible = true
            injectMethod.method.invoke(instance, *args)
        }
    }

    /**
     * 注入 @Value 属性值
     */
    fun injectValues(instance: Any, definition: BeanDefinition) {
        for (valueField in definition.valueFields) {
            val value = ValueResolver.resolve(valueField.expression, valueField.field.type)
            if (value != null) {
                valueField.field.set(instance, value)
            } else {
                warning(
                    "[IoC] @Value 注入失败: ${instance.javaClass.simpleName}.${valueField.field.name}" +
                        " (表达式=${valueField.expression})"
                )
            }
        }
    }

    /**
     * 解析依赖值
     */
    internal fun resolveDependency(type: Class<*>, nameQualifier: String?): Any? {
        // 如果有名称限定，按名称查找
        if (!nameQualifier.isNullOrEmpty()) {
            val definition = registry.getByName(nameQualifier)
            if (definition != null && type.isAssignableFrom(definition.type)) {
                return beanProvider(type, nameQualifier)
            }
            // registry 中未找到，回退到 beanProvider（可能是手动注册的 Bean）
            return beanProvider(type, nameQualifier)
        }

        // 按类型查找 — 统一委托给 beanProvider，由 BeanResolver 通过 @Primary 逻辑选择
        return beanProvider(type, null)
    }
}

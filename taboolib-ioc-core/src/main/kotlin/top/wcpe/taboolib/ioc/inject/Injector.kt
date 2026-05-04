package top.wcpe.taboolib.ioc.inject

import top.wcpe.taboolib.ioc.annotation.*
import top.wcpe.taboolib.ioc.bean.*
import top.wcpe.taboolib.ioc.cycle.CycleResolver
import top.wcpe.taboolib.ioc.util.KotlinPropertyAnnotations.findAnnotation
import top.wcpe.taboolib.ioc.util.KotlinPropertyAnnotations.hasAnnotation

/**
 * 注入器 - 协调实例化、属性装配与生命周期回调
 */
class Injector(
    private val fieldInjector: FieldInjector,
    private val beanProvider: (type: Class<*>, name: String?) -> Any?
) {

    constructor(
        registry: BeanRegistry,
        cycleResolver: CycleResolver,
        fieldInjector: FieldInjector
    ) : this(
        fieldInjector = fieldInjector,
        beanProvider = singletonOnlyBeanProvider(registry, cycleResolver)
    )

    /**
     * 创建 Bean 实例，但不执行字段/方法注入。
     * 支持构造函数实例化和 @Bean 工厂方法实例化。
     */
    fun instantiate(definition: BeanDefinition): Any {
        if (definition.isFactoryBean()) {
            return instantiateByFactory(definition)
        }
        val constructor = definition.constructor
            ?: throw IllegalStateException("BeanDefinition '${definition.name}' 缺少构造函数且不是工厂 Bean")
        val constructorArgs = resolveConstructorArgs(definition)
        return constructor.newInstance(*constructorArgs)
    }

    /**
     * 通过 @Bean 工厂方法创建实例。
     */
    private fun instantiateByFactory(definition: BeanDefinition): Any {
        val factoryBeanName = definition.factoryBeanName!!
        val factoryMethod = definition.factoryMethod!!

        val factoryInstance = beanProvider(Any::class.java, factoryBeanName)
            ?: throw IllegalStateException(
                "@Bean 方法 '${definition.name}' 的配置类 '$factoryBeanName' 尚未创建"
            )

        val args = resolveFactoryMethodArgs(definition)
        val result = factoryMethod.invoke(factoryInstance, *args)
        return result ?: throw IllegalStateException(
            "@Bean 方法 ${factoryInstance.javaClass.name}.${factoryMethod.name}() 返回了 null"
        )
    }

    /**
     * 解析工厂方法参数。
     */
    private fun resolveFactoryMethodArgs(definition: BeanDefinition): Array<Any?> {
        if (definition.constructorParameters.isEmpty()) {
            return emptyArray()
        }
        return definition.constructorParameters.mapIndexed { index, parameter ->
            val resolved = if (parameter.lazy) {
                fieldInjector.resolveLazyDependency(parameter.type, parameter.nameQualifier)
            } else {
                beanProvider(parameter.type, parameter.nameQualifier)
            }

            // 验证非 Lazy 参数不能为 null
            if (resolved == null && !parameter.lazy) {
                throw BeanInstantiationException.missingFactoryMethodParameter(
                    beanName = definition.name,
                    factoryBeanName = definition.factoryBeanName!!,
                    factoryMethodName = definition.factoryMethod!!.name,
                    parameterIndex = index,
                    parameterType = parameter.type,
                    nameQualifier = parameter.nameQualifier
                )
            }

            resolved
        }.toTypedArray()
    }

    /**
     * 执行字段与方法注入。
     * 对于 @Bean 工厂方法产物，如果实际实例类型与声明返回类型不同（如返回接口），
     * 会补充扫描实际类型上的 @Inject/@Value 字段和 @Inject 方法。
     */
    fun populate(instance: Any, definition: BeanDefinition) {
        fieldInjector.injectFields(instance, definition)
        fieldInjector.injectMethods(instance, definition)
        fieldInjector.injectValues(instance, definition)

        // 补充扫描：@Bean 返回接口类型时，实际实例可能有额外的注入点
        if (definition.isFactoryBean()) {
            val actualClass = instance.javaClass
            if (actualClass != definition.type) {
                val extraFields = resolveInjectFields(actualClass)
                    .filter { extra -> definition.injectFields.none { it.field == extra.field } }
                val extraMethods = resolveInjectMethods(actualClass)
                    .filter { extra -> definition.injectMethods.none { it.method == extra.method } }
                val extraValues = resolveValueFields(actualClass)
                    .filter { extra -> definition.valueFields.none { it.field == extra.field } }

                if (extraFields.isNotEmpty() || extraMethods.isNotEmpty() || extraValues.isNotEmpty()) {
                    val supplementDef = BeanDefinition(
                        name = definition.name,
                        type = actualClass,
                        constructor = null,
                        injectFields = extraFields,
                        injectMethods = extraMethods,
                        postConstruct = null,
                        postEnable = null,
                        preDestroy = null,
                        constructorParameters = emptyList(),
                        dependencies = emptyList(),
                        valueFields = extraValues
                    )
                    fieldInjector.injectFields(instance, supplementDef)
                    fieldInjector.injectMethods(instance, supplementDef)
                    fieldInjector.injectValues(instance, supplementDef)
                }
            }
        }
    }

    /**
     * 调用初始化回调。
     * 遍历所有 @PostConstruct 方法。
     * 对于 @Bean 工厂方法产物，如果实际类型与声明类型不同，补充扫描实际类型的回调。
     */
    fun invokePostConstruct(instance: Any, definition: BeanDefinition) {
        for (method in definition.postConstructMethods) {
            try {
                method.invoke(instance)
            } catch (e: java.lang.reflect.InvocationTargetException) {
                throw e.targetException ?: e
            }
        }
        // 补充扫描：@Bean 返回接口类型时，实际实例可能有额外的 @PostConstruct
        if (definition.isFactoryBean()) {
            val actualClass = instance.javaClass
            if (actualClass != definition.type) {
                val extraMethods = actualClass.declaredMethods.filter {
                    it.isAnnotationPresent(PostConstruct::class.java)
                }.filter { extra ->
                    definition.postConstructMethods.none { it.name == extra.name && it.parameterCount == extra.parameterCount }
                }
                for (method in extraMethods) {
                    method.isAccessible = true
                    try {
                        method.invoke(instance)
                    } catch (e: java.lang.reflect.InvocationTargetException) {
                        throw e.targetException ?: e
                    }
                }
            }
        }
    }

    /**
     * 解析构造函数参数。
     * 对标记了 @Lazy 的参数创建延迟代理，其余立即解析。
     */
    private fun resolveConstructorArgs(definition: BeanDefinition): Array<Any?> {
        if (definition.constructorParameters.isEmpty()) {
            return emptyArray()
        }

        return definition.constructorParameters.mapIndexed { index, parameter ->
            val resolved = if (parameter.lazy) {
                fieldInjector.resolveLazyDependency(parameter.type, parameter.nameQualifier)
            } else {
                beanProvider(parameter.type, parameter.nameQualifier)
            }

            // 验证非 Lazy 参数不能为 null
            if (resolved == null && !parameter.lazy) {
                throw BeanInstantiationException.missingConstructorParameter(
                    beanName = definition.name,
                    beanType = definition.type,
                    parameterIndex = index,
                    parameterType = parameter.type,
                    nameQualifier = parameter.nameQualifier
                )
            }

            resolved
        }.toTypedArray()
    }

    // ── 运行时补充扫描辅助方法 ──

    private fun resolveInjectFields(clazz: Class<*>): List<InjectField> {
        return clazz.declaredFields.mapNotNull { field ->
            val inject = field.hasAnnotation(Inject::class.java)
            val resource = field.findAnnotation(Resource::class.java)
            if (!inject && resource == null) return@mapNotNull null
            val named = field.findAnnotation(Named::class.java)
            val lazy = field.findAnnotation(Lazy::class.java)
            val injectAnnotation = field.findAnnotation(Inject::class.java)
            InjectField(
                field = field,
                requiredType = field.type,
                nameQualifier = resource?.name?.takeIf { it.isNotEmpty() }
                    ?: named?.value?.takeIf { it.isNotEmpty() },
                lazy = lazy?.value == true,
                required = injectAnnotation?.required ?: true
            )
        }
    }

    private fun resolveInjectMethods(clazz: Class<*>): List<InjectMethod> {
        return clazz.declaredMethods.filter { m ->
            !m.isSynthetic && !m.name.endsWith("\$annotations") &&
                (m.isAnnotationPresent(Inject::class.java) || m.isAnnotationPresent(Resource::class.java))
        }.map { m ->
            val resource = m.getAnnotation(Resource::class.java)
            val params = m.parameters.map { p ->
                val named = p.getAnnotation(Named::class.java)
                InjectParameter(
                    type = p.type,
                    nameQualifier = named?.value?.takeIf { it.isNotEmpty() }
                        ?: resource?.name?.takeIf { it.isNotEmpty() && m.parameterCount == 1 }
                )
            }
            InjectMethod(m, params)
        }
    }

    private fun resolveValueFields(clazz: Class<*>): List<ValueField> {
        return clazz.declaredFields.mapNotNull { field ->
            val value = field.getAnnotation(Value::class.java)
                ?: field.findAnnotation(Value::class.java)
                ?: return@mapNotNull null
            ValueField(field, value.value)
        }
    }

    companion object {

        private fun singletonOnlyBeanProvider(
            registry: BeanRegistry,
            cycleResolver: CycleResolver
        ): (Class<*>, String?) -> Any? {
            return { type, name ->
                if (name != null) {
                    val namedInstance = cycleResolver.getSingleton(name)
                    if (namedInstance != null && type.isInstance(namedInstance)) {
                        namedInstance
                    } else {
                        null
                    }
                } else {
                    null
                } ?: run {
                    val definition = if (name != null) {
                        registry.getByName(name)
                    } else {
                        registry.getPrimaryByType(type)
                    } ?: return@run null

                    cycleResolver.getSingleton(definition.name)?.takeIf(type::isInstance)
                }
            }
        }
    }
}

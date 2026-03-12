package top.wcpe.taboolib.ioc.inject

import top.wcpe.taboolib.ioc.bean.BeanDefinition
import top.wcpe.taboolib.ioc.bean.BeanRegistry
import top.wcpe.taboolib.ioc.cycle.CycleResolver

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
        return definition.constructorParameters.map { parameter ->
            if (parameter.lazy) {
                fieldInjector.resolveLazyDependency(parameter.type, parameter.nameQualifier)
            } else {
                beanProvider(parameter.type, parameter.nameQualifier)
            }
        }.toTypedArray()
    }

    /**
     * 执行字段与方法注入。
     */
    fun populate(instance: Any, definition: BeanDefinition) {
        fieldInjector.injectFields(instance, definition)
        fieldInjector.injectMethods(instance, definition)
        fieldInjector.injectValues(instance, definition)
    }

    /**
     * 调用初始化回调。
     * 遍历所有 @PostConstruct 方法。
     */
    fun invokePostConstruct(instance: Any, definition: BeanDefinition) {
        for (method in definition.postConstructMethods) {
            method.invoke(instance)
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

        return definition.constructorParameters.map { parameter ->
            if (parameter.lazy) {
                fieldInjector.resolveLazyDependency(parameter.type, parameter.nameQualifier)
            } else {
                beanProvider(parameter.type, parameter.nameQualifier)
            }
        }.toTypedArray()
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

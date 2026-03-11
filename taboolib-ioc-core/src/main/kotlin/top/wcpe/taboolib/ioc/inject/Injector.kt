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
     */
    fun instantiate(definition: BeanDefinition): Any {
        val constructorArgs = resolveConstructorArgs(definition)
        return definition.constructor.newInstance(*constructorArgs)
    }

    /**
     * 执行字段与方法注入。
     */
    fun populate(instance: Any, definition: BeanDefinition) {
        fieldInjector.injectFields(instance, definition)
        fieldInjector.injectMethods(instance, definition)
    }

    /**
     * 调用初始化回调。
     */
    fun invokePostConstruct(instance: Any, definition: BeanDefinition) {
        definition.postConstruct?.invoke(instance)
    }

    /**
     * 解析构造函数参数
     */
    private fun resolveConstructorArgs(definition: BeanDefinition): Array<Any?> {
        if (definition.constructorParameters.isEmpty()) {
            return emptyArray()
        }

        return definition.constructorParameters.map { parameter ->
            beanProvider(parameter.type, parameter.nameQualifier)
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

package top.wcpe.taboolib.ioc.inject

import top.wcpe.taboolib.ioc.bean.BeanDefinition
import top.wcpe.taboolib.ioc.bean.BeanRegistry
import top.wcpe.taboolib.ioc.cycle.CycleResolver

/**
 * 注入器 - 协调实例化、属性装配与生命周期回调
 */
class Injector(
    private val registry: BeanRegistry,
    private val cycleResolver: CycleResolver,
    private val fieldInjector: FieldInjector
) {

    /**
     * 创建 Bean 实例，但不执行字段/方法注入。
     */
    fun instantiate(definition: BeanDefinition): Any {
        cycleResolver.getSingleton(definition.name).let { (instance, _) ->
            if (instance != null) {
                return instance
            }
        }

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
            resolveBean(parameter.type, parameter.nameQualifier)
        }.toTypedArray()
    }

    /**
     * 解析 Bean
     */
    private fun resolveBean(type: Class<*>, name: String?): Any? {
        if (name != null) {
            cycleResolver.getSingleton(name).let { (instance, _) ->
                if (instance != null && type.isInstance(instance)) {
                    return instance
                }
            }
        }

        val definition = if (name != null) {
            registry.getByName(name)
        } else {
            registry.getPrimaryByType(type)
        } ?: return null

        cycleResolver.getSingleton(definition.name).let { (instance, _) ->
            if (instance != null && type.isInstance(instance)) return instance
        }

        return null
    }
}

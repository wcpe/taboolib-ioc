package top.wcpe.taboolib.ioc.inject

import top.wcpe.taboolib.ioc.annotation.Named
import top.wcpe.taboolib.ioc.bean.BeanDefinition
import top.wcpe.taboolib.ioc.bean.BeanRegistry
import top.wcpe.taboolib.ioc.cycle.CycleDetector
import top.wcpe.taboolib.ioc.cycle.CycleResolver

/**
 * 注入器 - 协调构造函数注入和字段注入
 */
class Injector(
    private val registry: BeanRegistry,
    private val cycleResolver: CycleResolver,
    private val cycleDetector: CycleDetector,
    private val constructorResolver: ConstructorResolver,
    private val fieldInjector: FieldInjector
) {

    /**
     * 创建 Bean 实例并注入依赖
     */
    fun createAndInject(definition: BeanDefinition): Any {
        val name = definition.name
        val isConstructorInjection = constructorResolver.isConstructorInjection(definition.constructor)

        // 检查是否已存在
        cycleResolver.getSingleton(name).let { (instance, _) ->
            if (instance != null) return instance
        }

        // 标记开始创建
        cycleDetector.beginCreation(name, isConstructorInjection)

        return try {
            // 1. 解析构造函数参数
            val constructorArgs = resolveConstructorArgs(definition)

            // 2. 创建实例
            val instance = definition.constructor.newInstance(*constructorArgs)

            // 3. 注册早期引用（解决循环依赖）
            cycleResolver.addEarlySingleton(name, instance)

            // 4. 注入字段依赖
            fieldInjector.injectFields(instance, definition)

            // 5. 注入方法依赖
            fieldInjector.injectMethods(instance, definition)

            // 6. 注册完整 Bean
            cycleResolver.addSingleton(name, instance)

            // 7. 调用 @PostConstruct
            definition.postConstruct?.invoke(instance)

            instance
        } finally {
            cycleDetector.endCreation(name)
        }
    }

    /**
     * 解析构造函数参数
     */
    private fun resolveConstructorArgs(definition: BeanDefinition): Array<Any?> {
        val constructor = definition.constructor
        if (constructor.parameterCount == 0) {
            return emptyArray()
        }

        val parameterTypes = constructor.parameterTypes
        val parameterAnnotations = constructor.parameterAnnotations

        return parameterTypes.mapIndexed { index, type ->
            val annotations = parameterAnnotations[index]
            val named = annotations.filterIsInstance<Named>().firstOrNull()
            resolveBean(type, named?.value)
        }.toTypedArray()
    }

    /**
     * 解析 Bean
     */
    private fun resolveBean(type: Class<*>, name: String?): Any? {
        // 先从缓存中查找
        cycleResolver.getSingleton(name ?: type.name).let { (instance, _) ->
            if (instance != null && type.isInstance(instance)) return instance
        }

        // 查找 Bean 定义
        val beanDef = if (name != null) {
            registry.getByName(name)
        } else {
            registry.getPrimaryByType(type)
        } ?: return null

        // 递归创建
        return createAndInject(beanDef)
    }
}

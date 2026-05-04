package top.wcpe.taboolib.ioc.scope

import top.wcpe.taboolib.ioc.annotation.PreDestroy
import top.wcpe.taboolib.ioc.bean.BeanDefinition
import top.wcpe.taboolib.ioc.bean.BeanRegistry
import top.wcpe.taboolib.ioc.bean.BeanScope
import java.util.concurrent.ConcurrentHashMap

/**
 * 可刷新作用域实现。
 *
 * Bean 实例会被缓存，可通过 [refresh] 方法清除缓存触发重建。
 * 刷新时会自动调用 Bean 的 @PreDestroy 方法释放资源。
 */
class RefreshBeanScope(
    private val registry: BeanRegistry
) : BeanScope {

    private val cache = ConcurrentHashMap<String, Any>()

    override fun get(name: String, definition: BeanDefinition, creator: () -> Any): Any {
        return cache.getOrPut(name) { creator() }
    }

    override fun clear() {
        // 销毁所有缓存的 Bean
        cache.keys.forEach { name ->
            destroyBean(name)
        }
        cache.clear()
    }

    /**
     * 刷新指定 Bean 或全部。
     *
     * @param name Bean 名称，为 null 时刷新全部
     */
    fun refresh(name: String? = null) {
        if (name != null) {
            destroyBean(name)
            cache.remove(name)
        } else {
            clear()
        }
    }

    /**
     * 销毁指定 Bean，调用其 @PreDestroy 方法。
     */
    private fun destroyBean(name: String) {
        val instance = cache[name] ?: return
        val definition = registry.getByName(name) ?: return

        // 收集所有 @PreDestroy 方法
        val preDestroyMethods = collectPreDestroyMethods(definition, instance)
        
        // 调用 @PreDestroy 方法
        for (method in preDestroyMethods) {
            try {
                method.invoke(instance)
            } catch (e: Exception) {
                // 记录错误但继续销毁流程
                System.err.println("[RefreshBeanScope] @PreDestroy 执行失败: $name.${method.name} - ${e.message}")
            }
        }
    }

    /**
     * 收集 Bean 的 @PreDestroy 方法。
     * 包括 BeanDefinition 中定义的方法和实际类型上的方法。
     */
    private fun collectPreDestroyMethods(
        definition: BeanDefinition,
        instance: Any
    ): List<java.lang.reflect.Method> {
        val definedMethods = definition.preDestroyMethods

        // 如果不是工厂 Bean，直接返回定义的方法
        if (!definition.isFactoryBean()) return definedMethods

        val actualClass = instance.javaClass
        // 如果实际类型与定义类型相同，直接返回
        if (actualClass == definition.type) return definedMethods

        // 补充扫描实际类型上的 @PreDestroy 方法
        val extraMethods = actualClass.declaredMethods.filter {
            it.isAnnotationPresent(PreDestroy::class.java)
        }.filter { extra ->
            // 避免重复
            definedMethods.none { it.name == extra.name && it.parameterCount == extra.parameterCount }
        }.onEach { it.isAccessible = true }

        return definedMethods + extraMethods
    }
}

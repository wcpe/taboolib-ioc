package top.wcpe.taboolib.ioc.cycle

import java.util.concurrent.ConcurrentHashMap

/**
 * 循环依赖解析器 - 二级缓存
 */
class CycleResolver {

    // 一级缓存：完整的 Bean 实例
    private val singletonObjects = ConcurrentHashMap<String, Any>()

    // 二级缓存：早期暴露的 Bean 实例（已创建但未完成注入）
    private val earlySingletonObjects = ConcurrentHashMap<String, Any>()

    /**
     * 获取 Bean 实例
     * @return Pair<实例, 是否为早期引用>
     */
    fun getSingleton(name: String): Pair<Any?, Boolean> {
        singletonObjects[name]?.let { return it to false }
        earlySingletonObjects[name]?.let { return it to true }
        return null to false
    }

    /**
     * 添加完整 Bean
     */
    fun addSingleton(name: String, instance: Any) {
        singletonObjects[name] = instance
        earlySingletonObjects.remove(name)
    }

    /**
     * 添加早期 Bean（用于解决循环依赖）
     */
    fun addEarlySingleton(name: String, instance: Any) {
        earlySingletonObjects[name] = instance
    }

    /**
     * 清空缓存
     */
    fun clear() {
        singletonObjects.clear()
        earlySingletonObjects.clear()
    }
}

package top.wcpe.taboolib.ioc.cycle

import java.util.concurrent.ConcurrentHashMap

/**
 * 单例解析缓存。
 */
class CycleResolver {

    private val singletonObjects = ConcurrentHashMap<String, Any>()

    /**
     * 获取 Bean 实例。
     */
    fun getSingleton(name: String): Any? = singletonObjects[name]

    /**
     * 添加完整 Bean
     */
    fun addSingleton(name: String, instance: Any) {
        singletonObjects[name] = instance
    }

    /**
     * 清空缓存
     */
    fun clear() {
        singletonObjects.clear()
    }
}

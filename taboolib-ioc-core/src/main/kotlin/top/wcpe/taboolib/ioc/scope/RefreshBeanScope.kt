package top.wcpe.taboolib.ioc.scope

import top.wcpe.taboolib.ioc.bean.BeanDefinition
import top.wcpe.taboolib.ioc.bean.BeanScope
import java.util.concurrent.ConcurrentHashMap

/**
 * 可刷新作用域实现。
 *
 * Bean 实例会被缓存，可通过 [refresh] 方法清除缓存触发重建。
 */
class RefreshBeanScope : BeanScope {

    private val cache = ConcurrentHashMap<String, Any>()

    override fun get(name: String, definition: BeanDefinition, creator: () -> Any): Any {
        return cache.getOrPut(name) { creator() }
    }

    override fun clear() {
        cache.clear()
    }

    /**
     * 刷新指定 Bean 或全部。
     *
     * @param name Bean 名称，为 null 时刷新全部
     */
    fun refresh(name: String? = null) {
        if (name != null) cache.remove(name) else cache.clear()
    }
}

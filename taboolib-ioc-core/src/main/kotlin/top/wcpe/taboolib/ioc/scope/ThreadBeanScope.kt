package top.wcpe.taboolib.ioc.scope

import top.wcpe.taboolib.ioc.bean.BeanDefinition
import top.wcpe.taboolib.ioc.bean.BeanScope
import java.util.concurrent.ConcurrentHashMap

/**
 * 线程作用域实现。
 *
 * 每个线程持有独立的 Bean 实例缓存，线程间互不影响。
 */
class ThreadBeanScope : BeanScope {

    private val threadLocal = ThreadLocal.withInitial { ConcurrentHashMap<String, Any>() }

    override fun get(name: String, definition: BeanDefinition, creator: () -> Any): Any {
        return threadLocal.get().getOrPut(name) { creator() }
    }

    override fun clear() {
        threadLocal.remove()
    }

    /**
     * 清理当前线程的所有缓存实例。
     */
    fun clearCurrentThread() {
        threadLocal.get().clear()
    }
}

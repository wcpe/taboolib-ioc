package top.wcpe.taboolib.ioc.scope

import top.wcpe.taboolib.ioc.bean.BeanDefinition
import top.wcpe.taboolib.ioc.bean.BeanScope
import java.util.concurrent.ConcurrentHashMap

/**
 * 线程作用域实现。
 *
 * 每个线程持有独立的 Bean 实例缓存，线程间互不影响。
 *
 * ## 线程池环境注意事项
 *
 * 在线程池环境中使用时，需要注意 ThreadLocal 内存泄漏问题：
 *
 * ```kotlin
 * executorService.submit {
 *     try {
 *         val bean = context.getBean(MyThreadScopedBean::class.java)
 *         // 使用 bean
 *     } finally {
 *         // 必须手动清理，防止线程复用时的内存泄漏
 *         threadScope.clear()
 *     }
 * }
 * ```
 *
 * ## 方法说明
 *
 * - [clear]: 完全移除当前线程的 ThreadLocal 存储，释放所有缓存实例。
 *   **推荐在线程池环境中使用**，确保线程归还到池中时不会保留数据。
 *
 * - [clearCurrentThread]: 仅清空当前线程的缓存 Map，但保留 ThreadLocal 本身。
 *   适用于需要在同一线程中重置缓存但继续使用的场景。
 *
 * @see BeanScope
 */
class ThreadBeanScope : BeanScope {

    private val threadLocal = ThreadLocal.withInitial { ConcurrentHashMap<String, Any>() }

    override fun get(name: String, definition: BeanDefinition, creator: () -> Any): Any {
        return threadLocal.get().getOrPut(name) { creator() }
    }

    /**
     * 完全移除当前线程的 ThreadLocal 存储。
     *
     * 此方法会调用 [ThreadLocal.remove]，彻底清理当前线程的数据，
     * 防止在线程池环境中因线程复用导致的内存泄漏。
     *
     * **在线程池环境中，应在任务结束时调用此方法。**
     */
    override fun clear() {
        threadLocal.remove()
    }

    /**
     * 清空当前线程的缓存实例，但保留 ThreadLocal 本身。
     *
     * 此方法仅清空缓存 Map 的内容，不移除 ThreadLocal。
     * 适用于需要在同一线程中重置缓存但继续使用的场景。
     *
     * 如果在线程池环境中使用，推荐使用 [clear] 方法以彻底清理。
     */
    fun clearCurrentThread() {
        threadLocal.get().clear()
    }
}

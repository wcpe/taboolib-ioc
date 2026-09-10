package top.wcpe.taboolib.ioc.scope

import top.wcpe.taboolib.ioc.bean.BeanDefinition
import top.wcpe.taboolib.ioc.bean.BeanScope
import java.util.Collections
import java.util.WeakHashMap
import java.util.concurrent.ConcurrentHashMap

/**
 * 线程作用域实现。
 *
 * 每个线程持有独立的 Bean 实例缓存，线程间互不影响。
 *
 * ## 线程池环境注意事项
 *
 * 在线程池环境中使用时，需要注意缓存清理问题：
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
 * ## 内存泄漏防护设计
 *
 * 仅靠 ThreadLocal 无法安全支撑 Bukkit 这类**线程池复用**环境：
 *
 * 1. 池化线程永不死亡，其缓存里的 Bean 实例永不释放；
 * 2. 更严重的是插件重载（`/plugman reload`）：旧容器 ThreadBeanScope 的
 *    缓存挂在**存活的服务器线程**上，value 里的 Bean 实例来自旧 ClassLoader
 *    → 旧 ClassLoader 及其加载的全部类无法 GC，反复 reload 即 Metaspace OOM。
 *
 * ## 单一注册表设计（A-P1-02 修复）
 *
 * 早期实现采用「快路径 ThreadLocal + 弱引用注册表」**双结构**：`currentMap()`
 * 先查 ThreadLocal，未命中则新建 map 并分别写入 ThreadLocal 与 registry。这两步
 * 写入与 [clearAllThreads] 之间并非原子，存在 TOCTOU 竞态窗口：
 *
 * ```
 * 线程 A: fastPath.get() == null
 * 线程 B: clearAllThreads()                  → registry.clear()
 * 线程 A: synchronized(registry) { registry[threadA] = map }   // 逃过清理
 * ```
 *
 * 结果是池化线程复用该 map，拿到本应被清理的旧 Bean 实例（旧 ClassLoader 泄漏）。
 *
 * 本实现改为**单一 registry**：
 *
 * - 只保留 [registry] 一个结构，移除作为历史遗留的快路径 ThreadLocal；
 * - `currentMap()` 的「查线程 → 没有就建并放入 → 返回」在**同一临界区**内完成；
 * - [clearAllThreads] 与 `currentMap()` 使用**同一把锁**（registry 的监视器），
 *   使二者严格线性化，清理不可能插在「读-建-写」之间。
 *
 * ### 为什么保留 WeakHashMap
 *
 * [registry] 仍是 `WeakHashMap<Thread, Map>` 的同步包装：**线程死亡后条目随弱键
 * 自动回收**，池化线程/临时线程退出后无需显式清理。换成 `ConcurrentHashMap` 会
 * 失去弱引用语义（强引用 Thread 对象本身），故不采用。
 *
 * ### 性能
 *
 * 每次 [get] 都要走一次 `currentMap()`，即一把全局监视器。相比旧实现的
 * ThreadLocal 快路径确实引入了全局锁，但：
 *
 * - 临界区极短（一次哈希查找，命中后立即释放），争用成本可控；
 * - 正确性优先：旧快路径带来的正是被修复的竞态；
 * - 若未来确有性能压力，可再叠加一个**受同一把锁保护**的快路径对象，
 *   只要其失效判定仍在临界区内完成即可保证线性化（当前不引入，避免复杂化）。
 *
 * ## 方法说明
 *
 * - [clear]: 完全移除当前线程的缓存条目。
 *   **推荐在线程池环境中使用**，确保线程归还到池中时不会保留数据。
 *
 * - [clearCurrentThread]: 仅清空当前线程的缓存 Map 内容，保留注册表条目。
 *   适用于需要在同一线程中重置缓存但继续使用的场景。
 *
 * - [clearAllThreads]: 清空**所有线程**的缓存内容（不由 BeanScope 接口暴露，
 *   仅供容器关闭流程调用）。
 *
 * @see BeanScope
 */
class ThreadBeanScope : BeanScope {

    /**
     * 全局注册表：key 为弱引用，线程死亡后条目自动回收；
     * 容器关闭时可一次性清空所有线程的缓存，无需在目标线程上执行 remove。
     *
     * 该对象同时充当 `currentMap()` 与 `clearAllThreads()` 的**共享锁**，
     * 保证「读-建-写」与「清空」严格线性化（A-P1-02）。
     */
    private val registry: MutableMap<Thread, MutableMap<String, Any>> =
        Collections.synchronizedMap(WeakHashMap<Thread, MutableMap<String, Any>>())

    /**
     * 返回当前线程的缓存 Map，必要时创建并登记。
     *
     * 「查线程 → 没有就建并放入 → 返回」**整体在同一临界区内**执行，
     * 因此 [clearAllThreads] 无法插在中间：要么在本次调用之前完成
     * （本次会新建一个干净 map），要么在本次调用之后完成（会清掉本次的 map）。
     */
    private fun currentMap(): MutableMap<String, Any> {
        synchronized(registry) {
            val existing = registry[Thread.currentThread()]
            if (existing != null) return existing
            val created = ConcurrentHashMap<String, Any>()
            registry[Thread.currentThread()] = created
            return created
        }
    }

    override fun get(name: String, definition: BeanDefinition, creator: () -> Any): Any {
        // getOrPut 在 ConcurrentHashMap 上对同一 key 是原子的，避免并发重复创建
        return currentMap().getOrPut(name) { creator() }
    }

    /**
     * 完全移除当前线程的缓存。
     *
     * 此方法清理当前线程的注册表条目，
     * 防止在线程池环境中因线程复用导致的内存泄漏。
     *
     * **在线程池环境中，应在任务结束时调用此方法。**
     */
    override fun clear() {
        synchronized(registry) { registry.remove(Thread.currentThread()) }
    }

    /**
     * 清空当前线程的缓存实例内容，但保留注册表条目。
     *
     * 此方法仅清空缓存 Map 的内容，不移除注册表条目。
     * 适用于需要在同一线程中重置缓存但继续使用的场景。
     *
     * 如果在线程池环境中使用，推荐使用 [clear] 方法以彻底清理。
     */
    fun clearCurrentThread() {
        currentMap().clear()
    }

    /**
     * 清空**所有线程**的缓存内容，断开其对 Bean 实例的引用。
     *
     * 与 `currentMap()` 共用同一把锁，保证与「读-建-写」严格线性化：
     * 本方法返回后，任何线程再次 [get] 都不会拿到清理前创建的实例
     * → 根治插件重载时的 ClassLoader 泄漏。
     * 由 [top.wcpe.taboolib.ioc.bean.BeanContainer] 的容器关闭流程调用。
     */
    fun clearAllThreads() {
        synchronized(registry) {
            registry.values.forEach { it.clear() }
            registry.clear()
        }
    }
}

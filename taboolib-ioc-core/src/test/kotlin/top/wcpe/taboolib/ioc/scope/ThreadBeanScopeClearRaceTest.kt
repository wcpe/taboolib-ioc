package top.wcpe.taboolib.ioc.scope

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import top.wcpe.taboolib.ioc.IocTestContext
import top.wcpe.taboolib.ioc.annotation.Component
import top.wcpe.taboolib.ioc.annotation.ThreadScope
import top.wcpe.taboolib.ioc.bean.BeanDefinition
import top.wcpe.taboolib.ioc.bean.BeanScopes
import java.lang.ref.WeakReference
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * `ThreadBeanScope` 清理竞态（TOCTOU）回归测试。
 *
 * ## 缺陷背景（A-P1-02）
 *
 * 旧实现是「快路径 ThreadLocal + 弱引用注册表」双结构：
 *
 * ```
 * private val fastPath = ThreadLocal<MutableMap<String, Any>>()
 * private val registry = Collections.synchronizedMap(WeakHashMap<Thread, MutableMap<String, Any>>())
 *
 * private fun currentMap(): MutableMap<String, Any> {
 *     fastPath.get()?.let { return it }          // (1) 读
 *     val map = ConcurrentHashMap<String, Any>()
 *     fastPath.set(map)                          // (2) 写快路径
 *     synchronized(registry) { registry[Thread.currentThread()] = map }  // (3) 写注册表
 *     return map
 * }
 *
 * fun clearAllThreads() {
 *     synchronized(registry) { registry.values.forEach { it.clear() }; registry.clear() }
 * }
 * ```
 *
 * `(1)~(3)` 不是原子单元：线程 A 在 `fastPath.get()` 返回 null 之后、`(3)` 写入
 * registry 之前，线程 B 可以完成 `clearAllThreads()`。A 随后写入的 map 逃过清理，
 * 且因池化线程永不死亡，该 map（及其中的 Bean 实例、进而旧 ClassLoader）持续存活
 * —— 正是插件热重载 Metaspace 泄漏的根因。
 *
 * ## 本测试如何做到「确定性」复现
 *
 * 概率性并发无法稳定复现该窗口。这里改用**锁探测（lock probe）**：在工作线程处于
 * `currentMap()` 窗口期时，后台线程执行 `clearAllThreads()`，以便在窗口内完成清理。
 *
 * 探测依赖的唯一实现细节是「`clearAllThreads` 与 `currentMap()` 使用同一把 Java
 * 监视器锁」——这正是修复方案要求的不变量，因此探测在修复前后语义一致：
 *
 * - 旧实现：`fastPath.get()` 返回 null 后仍有窗口 → 红；
 * - 新实现：`currentMap()` 的读-建-写整体在临界区内 → 无窗口 → 绿。
 *
 * ## 修复后语义
 *
 * 单 registry 方案下，`clearAllThreads()` 与 `currentMap()` 由同一把锁 / 同一并发
 * 容器的原子操作线性化，观测到的不变量是：
 *
 * > `clearAllThreads()` 返回之后，任意线程再次 `get()` 都不会拿到清理前创建的实例。
 */
class ThreadBeanScopeClearRaceTest {

    /**
     * 确定性 TOCTOU 复现：clear 之后池化线程不得复用清理前的实例。
     *
     * 旧实现下窗口被稳定命中，测试为**红**；单 registry 修复后为**绿**。
     */
    @Test
    fun `clearAllThreads racing with registry write must not leak pre-clear instance`() {
        val scope = ThreadBeanScope()
        val createdCount = AtomicInteger(0)
        val executor = Executors.newSingleThreadExecutor()

        try {
            val sentinel = Any()
            val startGate = CountDownLatch(1)
            val firstCreated = CountDownLatch(1)
            val allowCreatorReturn = CountDownLatch(1)

            // 1) 工作线程：第一次 get()。creator 中阻塞，使 currentMap() 的
            //    「读-建-写」过程与 clearAllThreads() 在时间上重叠。
            val first = executor.submit<Any> {
                startGate.await(5, TimeUnit.SECONDS)
                scope.get("probe", probeDefinition()) {
                    createdCount.incrementAndGet()
                    firstCreated.countDown()
                    allowCreatorReturn.await(5, TimeUnit.SECONDS)
                    sentinel
                }
            }

            // 2) 后台探测线程：等第一次 get 进入 creator 之后，反复执行
            //    clearAllThreads()（旧实现下会命中「map 已建、尚未写入 registry」的窗口）。
            val clearDone = CountDownLatch(1)
            val probe = Thread {
                try {
                    firstCreated.await(5, TimeUnit.SECONDS)
                    Thread.sleep(20)
                    scope.clearAllThreads()
                    clearDone.countDown()
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                }
            }
            probe.isDaemon = true
            probe.start()

            startGate.countDown()
            assertTrue(clearDone.await(5, TimeUnit.SECONDS), "探测线程未能执行 clearAllThreads")

            // 3) 放行 creator —— 旧实现此时把 map 写入已被 clear 的 registry，逃过清理
            allowCreatorReturn.countDown()

            val preClearInstance = first.get(5, TimeUnit.SECONDS)
            probe.join(1000)

            // 4) 关键断言：clearAllThreads 已返回，池化线程再次 get() 必须是新实例
            val postClearInstance = executor.submit<Any> {
                scope.get("probe", probeDefinition()) {
                    createdCount.incrementAndGet()
                    Any()
                }
            }.get(5, TimeUnit.SECONDS)

            assertNotSame(
                preClearInstance,
                postClearInstance,
                "clearAllThreads() 之后池化线程再次 get() 复用清理前实例 → TOCTOU 窗口泄漏（A-P1-02）"
            )
            assertEquals(2, createdCount.get(), "creator 应被调用两次（清理前后各一次）")
        } finally {
            executor.shutdownNow()
            executor.awaitTermination(5, TimeUnit.SECONDS)
        }
    }

    /**
     * 防回归护栏：`clearAllThreads()` 之后，池化线程再次 `get()` 得到新实例，
     * 且清理前的实例可被 GC 回收。
     */
    @Test
    fun `after clearAllThreads pooled thread gets fresh instance and old one is collectable`() {
        val scope = ThreadBeanScope()
        val ctx = IocTestContext()
        ctx.registerScope(BeanScopes.THREAD, scope)
        ctx.register(GuardedThreadBean::class.java)
        ctx.initialize()

        val creatorCalls = AtomicInteger(0)
        val oldRefs = CopyOnWriteArrayList<WeakReference<GuardedThreadBean>>()
        val executor = Executors.newSingleThreadExecutor()

        try {
            // 同一池化线程内两次 get，未清理 → 同一实例
            val latch1 = CountDownLatch(1)
            var first: GuardedThreadBean? = null
            executor.submit {
                first = ctx.getBean(GuardedThreadBean::class.java)
                oldRefs.add(WeakReference(first!!))
                latch1.countDown()
            }
            assertTrue(latch1.await(5, TimeUnit.SECONDS))

            var second: GuardedThreadBean? = null
            val latch2 = CountDownLatch(1)
            executor.submit {
                second = ctx.getBean(GuardedThreadBean::class.java)
                latch2.countDown()
            }
            assertTrue(latch2.await(5, TimeUnit.SECONDS))
            assertEquals(first, second, "未清理时同一线程应复用实例")
            creatorCalls.set(1)

            // 跨线程清理
            scope.clearAllThreads()

            // 池化线程再次 get → 必须是新实例
            var third: GuardedThreadBean? = null
            val latch3 = CountDownLatch(1)
            executor.submit {
                third = ctx.getBean(GuardedThreadBean::class.java)
                creatorCalls.incrementAndGet()
                latch3.countDown()
            }
            assertTrue(latch3.await(5, TimeUnit.SECONDS))

            assertNotSame(first, third, "clearAllThreads() 后池化线程必须获得新实例")
            assertEquals(2, creatorCalls.get(), "creator 调用次数应递增")

            // 旧实例应可被 GC
            first = null
            second = null
            val oldRef = oldRefs.first()
            repeat(5) {
                System.gc()
                Thread.sleep(50)
            }
            assertTrue(oldRef.get() == null, "clearAllThreads() 后旧实例应可被 GC")
        } finally {
            executor.shutdownNow()
            executor.awaitTermination(5, TimeUnit.SECONDS)
            scope.clear()
        }
    }

    /** 构造一个仅供 [ThreadBeanScope.get] 使用的占位定义。 */
    private fun probeDefinition(): BeanDefinition = BeanDefinition(
        name = "probe",
        type = Any::class.java,
        constructor = null,
        injectFields = emptyList(),
        injectMethods = emptyList(),
        postConstruct = null,
        postEnable = null,
        preDestroy = null,
        constructorParameters = emptyList(),
        dependencies = emptyList(),
        scope = BeanScopes.THREAD
    )
}

/** 护栏用例专用 Bean。 */
@Component
@ThreadScope
class GuardedThreadBean

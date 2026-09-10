package top.wcpe.taboolib.ioc.bean

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import top.wcpe.taboolib.ioc.annotation.Component
import top.wcpe.taboolib.ioc.annotation.Inject
import top.wcpe.taboolib.ioc.annotation.Named
import top.wcpe.taboolib.ioc.annotation.PostConstruct

/**
 * A-P0-02 回归：**初始化前手动注册的 Bean 不得以未注入裸实例提前暴露**。
 *
 * 既有实现中 `BeanContainer.registerBean` 在「容器未初始化」分支会同时写
 * `manualBeansByName` 与 `cycleResolver`，导致一个尚未注入、尚未执行
 * `@PostConstruct` 的**半成品实例**，在 `initializing` 窗口内即可被其他 Bean
 * 依赖解析命中。
 *
 * 红用例判据：
 * - 一个依赖「初始化前注册的手动 Bean」的**扫描 Bean**，其注入字段拿到的实例
 *   必须**已经完成 `@PostConstruct`**（即不是裸半成品）。
 *
 * 同时保留既有能力：**「扫描 Bean 注入手动 Bean」必须不回归**（见第二个用例）。
 */
class PendingManualBeanEarlyExposureTest {

    @BeforeEach
    fun setup() {
        BeanContainer.resetForTesting()
    }

    @AfterEach
    fun teardown() {
        BeanContainer.resetForTesting()
    }

    /**
     * 红用例：`initializing` 窗口内，依赖方不得拿到未完成生命周期的手动 Bean。
     *
     * 构造顺序敏感场景：先注册 `consumer`（依赖 `lateManual`），再注册 `lateManual`。
     * 重放时先补全 `consumer`，此刻 `lateManual` **尚未补全生命周期**。
     *
     * - **缺陷行为（红）**：既有实现把 `lateManual` 的裸实例提前写入 `cycleResolver`，
     *   于是 `consumer` **静默注入到 `ready=false` 的半成品**（观察到 `false`）。
     * - **修复后（绿）**：要么重放**显式失败**（不再静默注入半成品），
     *   要么注入到的是已完成实例。无论哪种，都**绝不能观察到 `ready=false` 的半成品**。
     *
     * 判据：记录注入时刻被依赖者的 `ready` 状态；若注入成功则必须为 `true`，
     * 若抛异常则视为「显式失败」（可接受的安全行为）。
     */
    @Test
    fun `initializing 窗口内依赖方不应拿到未注入完成的手动 Bean`() {
        val manual = LateInitializedManualBean()
        ManualConsumerOfLateBean.observedReadyAtInjection = null

        // 顺序敏感：先注册「依赖方」，再注册「被依赖的手动 Bean」
        BeanContainer.registerBean("consumerManual", ManualConsumerOfLateBean())
        BeanContainer.registerBean("lateManual", manual)

        assertFalse(manual.ready, "注册瞬间不应已 ready（@PostConstruct 尚未执行）")

        // 初始化：修复前此处静默成功但注入了半成品；修复后要么显式失败，要么注入完成品
        val initFailure: Throwable? = try {
            BeanContainer.initialize()
            null
        } catch (t: Throwable) {
            t
        }

        val observed = ManualConsumerOfLateBean.observedReadyAtInjection
        assertFalse(
            observed == false,
            "initializing 窗口内依赖方**不得**拿到未完成 @PostConstruct 的手动 Bean 半成品（A-P0-02）。" +
                "observed=$observed, initFailure=${initFailure?.message}"
        )

        if (initFailure == null) {
            // 初始化成功 → 手动 Bean 必须已完成生命周期
            val finalManual = BeanContainer.getBean(LateInitializedManualBean::class.java, "lateManual")
            assertNotNull(finalManual, "初始化完成后应能解析到手动 Bean")
            assertTrue(finalManual!!.ready, "手动 Bean 初始化完成后必须 ready")
        }
    }

    /**
     * 防回归：**扫描 Bean 注入手动 Bean** 的既有能力不得丢失。
     *
     * 先 `registerBean` 手动 Bean（容器未初始化），再注册一个依赖它的扫描 Bean，
     * `initialize()` 后扫描 Bean 必须成功拿到手动 Bean 实例。
     */
    @Test
    fun `扫描 Bean 注入初始化前注册的手动 Bean 不回归`() {
        val manual = SimpleManualDependency("hello")

        // 容器未初始化时注册手动 Bean
        BeanContainer.registerBean("manualDep", manual)

        // 注册依赖手动 Bean 的扫描 Bean
        registerScanned(ConsumerOfManual::class.java)

        // 初始化容器
        BeanContainer.initialize()

        // 扫描 Bean 应能注入到手动 Bean
        val consumer = BeanContainer.getBean(ConsumerOfManual::class.java)
        assertNotNull(consumer, "依赖手动 Bean 的扫描 Bean 应被创建")
        assertNotNull(consumer!!.manualDep, "扫描 Bean 必须能注入初始化前注册的手动 Bean")
        assertEquals("hello", consumer.manualDep!!.value())
    }

    /**
     * 通过 BeanContainer 的 ClassScanner 扫描组件类并写入注册表，
     * 等价于运行期组件扫描注册（BeanContainer.initialize 不会自行扫描包）。
     */
    private fun registerScanned(clazz: Class<*>) {
        val definition = BeanContainer.getScanner().scan(clazz)
            ?: error("扫描组件失败: ${clazz.name}")
        BeanContainer.getRegistry().register(definition)
    }

    // ==================== 测试夹具 ====================

    /** 手动注册的 Bean，其 @PostConstruct 设置 ready 标记 */
    class LateInitializedManualBean {
        @Volatile
        var ready: Boolean = false

        @PostConstruct
        fun init() {
            ready = true
        }
    }

    /** 手动注册的简单依赖 Bean */
    class SimpleManualDependency(private val text: String) {
        fun value(): String = text
    }

    /**
     * 手动注册的依赖方：依赖 [LateInitializedManualBean]。
     * 在自身 `@PostConstruct` 中记录被注入实例的 `ready` 状态 ——
     * 即「注入时刻」被依赖者是否已完成生命周期。
     */
    class ManualConsumerOfLateBean {
        companion object {
            @Volatile
            var observedReadyAtInjection: Boolean? = null
        }

        @Inject
        @Named("lateManual")
        var lateManual: LateInitializedManualBean? = null

        @PostConstruct
        fun observe() {
            observedReadyAtInjection = lateManual?.ready
        }
    }

    /** 扫描 Bean：依赖手动注册的 SimpleManualDependency */
    @Component
    class ConsumerOfManual {
        @Inject
        @Named("manualDep")
        var manualDep: SimpleManualDependency? = null
    }
}

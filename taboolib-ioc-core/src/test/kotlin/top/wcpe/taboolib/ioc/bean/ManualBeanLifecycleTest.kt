package top.wcpe.taboolib.ioc.bean

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import top.wcpe.taboolib.ioc.annotation.*

/**
 * 手动注册 Bean 的生命周期管理测试
 *
 * 验证手动注册的 Bean 是否经过完整的生命周期处理：
 * - 属性注入（@Inject 字段和方法）
 * - BeanPostProcessor 回调
 * - @PostConstruct 回调
 * - AOP 代理包装
 * - @PreDestroy 回调（容器关闭时）
 */
class ManualBeanLifecycleTest {

    @BeforeEach
    fun setup() {
        BeanContainer.resetForTesting()
    }

    @AfterEach
    fun teardown() {
        BeanContainer.resetForTesting()
    }

    @Test
    fun `手动注册的 Bean 应该执行字段注入`() {
        // 准备：注册依赖 Bean
        val dependency = DependencyBean()
        BeanContainer.registerBean("dependency", dependency)

        // 初始化容器
        setInitialized(true)

        // 注册带有 @Inject 字段的 Bean
        val manualBean = ManualBeanWithInject()
        assertNull(manualBean.dependency, "注入前字段应为 null")

        BeanContainer.registerBean("manualBean", manualBean)

        // 验证：字段已被注入
        assertNotNull(manualBean.dependency, "字段应该被注入")
        assertSame(dependency, manualBean.dependency, "注入的应该是同一个实例")
    }

    @Test
    fun `手动注册的 Bean 应该执行 PostConstruct 回调`() {
        setInitialized(true)

        val manualBean = ManualBeanWithPostConstruct()
        assertFalse(manualBean.initialized, "PostConstruct 前应为 false")

        BeanContainer.registerBean("manualBean", manualBean)

        // 验证：@PostConstruct 已被调用
        assertTrue(manualBean.initialized, "@PostConstruct 应该被执行")
    }

    @Test
    fun `手动注册的 Bean 应该执行 PreDestroy 回调`() {
        setInitialized(true)

        val manualBean = ManualBeanWithPreDestroy()
        BeanContainer.registerBean("manualBean", manualBean)

        assertFalse(manualBean.destroyed, "PreDestroy 前应为 false")

        // 关闭容器
        BeanContainer.shutdown()

        // 验证：@PreDestroy 已被调用
        assertTrue(manualBean.destroyed, "@PreDestroy 应该被执行")
    }

    @Test
    fun `手动注册的 Bean 可以被其他 Bean 依赖`() {
        setInitialized(true)

        // 先注册手动 Bean
        val manualBean = DependencyBean()
        BeanContainer.registerBean("manualDependency", manualBean)

        // 再注册依赖它的 Bean
        val consumerBean = ManualBeanWithNamedInject()
        BeanContainer.registerBean("consumer", consumerBean)

        // 验证：依赖注入成功
        assertNotNull(consumerBean.manualDependency, "应该能注入手动注册的 Bean")
        assertSame(manualBean, consumerBean.manualDependency, "注入的应该是同一个实例")
    }

    @Test
    fun `手动注册的 Bean 应该执行方法注入`() {
        setInitialized(true)

        val dependency = DependencyBean()
        BeanContainer.registerBean("dependency", dependency)

        val manualBean = ManualBeanWithMethodInject()
        assertNull(manualBean.injectedDependency, "方法注入前应为 null")

        BeanContainer.registerBean("manualBean", manualBean)

        // 验证：方法注入已执行
        assertNotNull(manualBean.injectedDependency, "方法注入应该被执行")
        assertSame(dependency, manualBean.injectedDependency, "注入的应该是同一个实例")
    }

    @Test
    fun `手动注册的 Bean 应该支持多个 PostConstruct 方法`() {
        setInitialized(true)

        val manualBean = ManualBeanWithMultiplePostConstruct()
        assertEquals(0, manualBean.callCount, "初始调用次数应为 0")

        BeanContainer.registerBean("manualBean", manualBean)

        // 验证：所有 @PostConstruct 方法都被调用
        assertEquals(2, manualBean.callCount, "应该调用 2 个 @PostConstruct 方法")
    }

    @Test
    fun `手动注册的 Bean 应该支持多个 PreDestroy 方法`() {
        setInitialized(true)

        val manualBean = ManualBeanWithMultiplePreDestroy()
        BeanContainer.registerBean("manualBean", manualBean)

        assertEquals(0, manualBean.destroyCount, "初始销毁次数应为 0")

        BeanContainer.shutdown()

        // 验证：所有 @PreDestroy 方法都被调用
        assertEquals(2, manualBean.destroyCount, "应该调用 2 个 @PreDestroy 方法")
    }

    @Test
    fun `手动注册失败时应该清理状态`() {
        setInitialized(true)

        // 注册一个会导致注入失败的 Bean（缺少依赖）
        val manualBean = ManualBeanWithInject()

        assertThrows(IllegalStateException::class.java) {
            BeanContainer.registerBean("manualBean", manualBean)
        }

        // 验证：失败的 Bean 不应该被缓存
        assertFalse(BeanContainer.containsBean("manualBean"), "失败的 Bean 不应该被注册")
    }

    // 通过反射设置 initialized 字段
    private fun setInitialized(value: Boolean) {
        val field = BeanContainer::class.java.getDeclaredField("initialized")
        field.isAccessible = true
        field.set(BeanContainer, value)
    }

    // 测试用的 Bean 类

    class DependencyBean

    class ManualBeanWithInject {
        @Inject
        var dependency: DependencyBean? = null
    }

    class ManualBeanWithPostConstruct {
        var initialized = false

        @PostConstruct
        fun init() {
            initialized = true
        }
    }

    class ManualBeanWithPreDestroy {
        var destroyed = false

        @PreDestroy
        fun destroy() {
            destroyed = true
        }
    }

    class ManualBeanWithNamedInject {
        @Inject
        @Named("manualDependency")
        var manualDependency: DependencyBean? = null
    }

    class ManualBeanWithMethodInject {
        var injectedDependency: DependencyBean? = null

        @Inject
        fun setDependency(dependency: DependencyBean) {
            injectedDependency = dependency
        }
    }

    class ManualBeanWithMultiplePostConstruct {
        var callCount = 0

        @PostConstruct
        fun init1() {
            callCount++
        }

        @PostConstruct
        fun init2() {
            callCount++
        }
    }

    class ManualBeanWithMultiplePreDestroy {
        var destroyCount = 0

        @PreDestroy
        fun destroy1() {
            destroyCount++
        }

        @PreDestroy
        fun destroy2() {
            destroyCount++
        }
    }
}

package top.wcpe.taboolib.ioc.bean

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import top.wcpe.taboolib.ioc.annotation.Component
import top.wcpe.taboolib.ioc.annotation.PostConstruct
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

/**
 * BeanContainer 并发初始化测试
 */
class BeanContainerConcurrencyTest {

    @BeforeEach
    fun setup() {
        BeanContainer.resetForTesting()
    }

    @AfterEach
    fun tearDown() {
        BeanContainer.resetForTesting()
    }

    @Test
    fun `测试多线程并发初始化 - 初始化逻辑只执行一次`() {
        // 注册测试 Bean
        BeanContainer.getRegistry().register(
            BeanDefinition(
                name = "testBean",
                type = TestBeanWithCounter::class.java,
                constructor = TestBeanWithCounter::class.java.getDeclaredConstructor(),
                injectFields = emptyList(),
                injectMethods = emptyList(),
                postConstruct = TestBeanWithCounter::class.java.getDeclaredMethod("init"),
                postEnable = null,
                preDestroy = null,
                constructorParameters = emptyList(),
                dependencies = emptyList(),
                scope = BeanScopes.SINGLETON
            )
        )

        val threadCount = 10
        val barrier = CyclicBarrier(threadCount)
        val latch = CountDownLatch(threadCount)
        val exceptions = mutableListOf<Throwable>()

        // 启动多个线程同时调用 initialize()
        val threads = (1..threadCount).map {
            thread {
                try {
                    // 等待所有线程就绪
                    barrier.await(5, TimeUnit.SECONDS)
                    // 同时调用初始化
                    BeanContainer.initialize()
                } catch (e: Throwable) {
                    synchronized(exceptions) {
                        exceptions.add(e)
                    }
                } finally {
                    latch.countDown()
                }
            }
        }

        // 等待所有线程完成
        assertTrue(latch.await(10, TimeUnit.SECONDS), "所有线程应在超时前完成")

        // 等待线程结束
        threads.forEach { it.join(1000) }

        // 验证没有异常
        if (exceptions.isNotEmpty()) {
            fail<Unit>("初始化过程中发生异常: ${exceptions.joinToString { it.message ?: it.toString() }}")
        }

        // 验证容器已初始化
        assertTrue(BeanContainer.initialized, "容器应该已初始化")

        // 获取 Bean 实例并验证 PostConstruct 只执行了一次
        val testBean = BeanContainer.getBean(TestBeanWithCounter::class.java)
        assertNotNull(testBean, "应该能获取到 Bean")
        assertEquals(1, testBean!!.initCount.get(), "PostConstruct 应该只执行一次")
    }

    @Test
    fun `测试多线程并发初始化 - 所有线程都能获取初始化后的容器`() {
        // 注册测试 Bean
        BeanContainer.getRegistry().register(
            BeanDefinition(
                name = "testService",
                type = TestService::class.java,
                constructor = TestService::class.java.getDeclaredConstructor(),
                injectFields = emptyList(),
                injectMethods = emptyList(),
                postConstruct = null,
                postEnable = null,
                preDestroy = null,
                constructorParameters = emptyList(),
                dependencies = emptyList(),
                scope = BeanScopes.SINGLETON
            )
        )

        val threadCount = 20
        val barrier = CyclicBarrier(threadCount)
        val latch = CountDownLatch(threadCount)
        val results = mutableListOf<TestService?>()
        val exceptions = mutableListOf<Throwable>()

        // 启动多个线程同时初始化并获取 Bean
        val threads = (1..threadCount).map {
            thread {
                try {
                    barrier.await(5, TimeUnit.SECONDS)
                    BeanContainer.initialize()
                    val bean = BeanContainer.getBean(TestService::class.java)
                    synchronized(results) {
                        results.add(bean)
                    }
                } catch (e: Throwable) {
                    synchronized(exceptions) {
                        exceptions.add(e)
                    }
                } finally {
                    latch.countDown()
                }
            }
        }

        assertTrue(latch.await(10, TimeUnit.SECONDS), "所有线程应在超时前完成")
        threads.forEach { it.join(1000) }

        // 验证没有异常
        if (exceptions.isNotEmpty()) {
            fail<Unit>("获取 Bean 过程中发生异常: ${exceptions.joinToString { it.message ?: it.toString() }}")
        }

        // 验证所有线程都获取到了 Bean
        assertEquals(threadCount, results.size, "所有线程都应该获取到 Bean")
        assertTrue(results.all { it != null }, "所有 Bean 都不应为 null")

        // 验证所有线程获取的是同一个单例实例
        val firstBean = results.first()
        assertTrue(results.all { it === firstBean }, "所有线程应该获取到同一个单例实例")
    }

    @Test
    fun `测试初始化失败的情况 - 容器状态正确恢复`() {
        // 注册一个会在初始化时抛出异常的 Bean
        BeanContainer.getRegistry().register(
            BeanDefinition(
                name = "failingBean",
                type = FailingBean::class.java,
                constructor = FailingBean::class.java.getDeclaredConstructor(),
                injectFields = emptyList(),
                injectMethods = emptyList(),
                postConstruct = FailingBean::class.java.getDeclaredMethod("init"),
                postEnable = null,
                preDestroy = null,
                constructorParameters = emptyList(),
                dependencies = emptyList(),
                scope = BeanScopes.SINGLETON
            )
        )

        // 尝试初始化（应该失败）
        assertThrows(RuntimeException::class.java) {
            BeanContainer.initialize()
        }

        // 验证容器状态
        assertFalse(BeanContainer.initialized, "初始化失败后容器不应标记为已初始化")

        // 清理并重新测试
        BeanContainer.resetForTesting()

        // 注册正常的 Bean
        BeanContainer.getRegistry().register(
            BeanDefinition(
                name = "normalBean",
                type = TestService::class.java,
                constructor = TestService::class.java.getDeclaredConstructor(),
                injectFields = emptyList(),
                injectMethods = emptyList(),
                postConstruct = null,
                postEnable = null,
                preDestroy = null,
                constructorParameters = emptyList(),
                dependencies = emptyList(),
                scope = BeanScopes.SINGLETON
            )
        )

        // 再次初始化应该成功
        assertDoesNotThrow {
            BeanContainer.initialize()
        }

        assertTrue(BeanContainer.initialized, "重新初始化应该成功")
    }

    @Test
    fun `测试并发初始化时的状态一致性`() {
        val threadCount = 15
        val barrier = CyclicBarrier(threadCount)
        val latch = CountDownLatch(threadCount)
        val initializedStates = mutableListOf<Boolean>()

        val threads = (1..threadCount).map {
            thread {
                try {
                    barrier.await(5, TimeUnit.SECONDS)
                    BeanContainer.initialize()

                    // 记录初始化后的状态
                    synchronized(initializedStates) {
                        initializedStates.add(BeanContainer.initialized)
                    }
                } catch (e: Throwable) {
                    // 忽略异常，只关注状态
                } finally {
                    latch.countDown()
                }
            }
        }

        assertTrue(latch.await(10, TimeUnit.SECONDS))
        threads.forEach { it.join(1000) }

        // 验证最终状态
        assertTrue(BeanContainer.initialized, "容器应该已初始化")
        assertTrue(initializedStates.all { it }, "所有线程看到的最终状态都应该是已初始化")
    }

    @Test
    fun `测试重复初始化是安全的`() {
        // 注册测试 Bean
        BeanContainer.getRegistry().register(
            BeanDefinition(
                name = "testBean",
                type = TestBeanWithCounter::class.java,
                constructor = TestBeanWithCounter::class.java.getDeclaredConstructor(),
                injectFields = emptyList(),
                injectMethods = emptyList(),
                postConstruct = TestBeanWithCounter::class.java.getDeclaredMethod("init"),
                postEnable = null,
                preDestroy = null,
                constructorParameters = emptyList(),
                dependencies = emptyList(),
                scope = BeanScopes.SINGLETON
            )
        )

        // 第一次初始化
        BeanContainer.initialize()
        
        // 获取 Bean 实例并验证初始化逻辑执行了一次
        val testBean = BeanContainer.getBean(TestBeanWithCounter::class.java)
        assertNotNull(testBean, "应该能获取到 Bean")
        assertEquals(1, testBean!!.initCount.get(), "第一次初始化后计数应为1")

        // 多次重复初始化
        repeat(5) {
            BeanContainer.initialize()
        }

        // 验证初始化逻辑只执行了一次
        assertEquals(1, testBean.initCount.get(), "即使多次调用 initialize()，初始化逻辑也应该只执行一次")
    }

    // 测试用的 Bean 类
    @Component
    class TestService {
        fun doSomething() = "done"
    }

    @Component
    class TestBeanWithCounter {
        val initCount = AtomicInteger(0)

        @PostConstruct
        fun init() {
            // 模拟初始化耗时
            Thread.sleep(10)
            initCount.incrementAndGet()
        }
    }

    @Component
    class FailingBean {
        @PostConstruct
        fun init() {
            throw RuntimeException("初始化失败")
        }
    }
}

package top.wcpe.taboolib.ioc.bean

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * BeanContainer Kotlin 扩展方法测试
 *
 * 注意：这些扩展方法依赖 BeanContainer 单例，
 * 在纯单元测试中需要通过 BeanContainer 内部方法设置状态。
 */
class BeanContainerExtensionsTest {

    @Test
    fun `bean 获取已注册的 Bean`() {
        BeanContainer.resetForTesting()
        BeanContainer.registerBean("extTestBean", ExtTestBean())

        // 手动设置 initialized 状态以便测试
        setInitialized(true)
        try {
            val result = bean<ExtTestBean>("extTestBean")
            assertNotNull(result)
        } finally {
            BeanContainer.resetForTesting()
        }
    }

    @Test
    fun `bean 找不到时抛出异常`() {
        BeanContainer.resetForTesting()
        setInitialized(true)
        try {
            assertThrows(IllegalStateException::class.java) {
                bean<ExtTestBean>()
            }
        } finally {
            BeanContainer.resetForTesting()
        }
    }

    @Test
    fun `beanOrNull 找不到时返回 null`() {
        BeanContainer.resetForTesting()
        setInitialized(true)
        try {
            val result = beanOrNull<ExtTestBean>()
            assertNull(result)
        } finally {
            BeanContainer.resetForTesting()
        }
    }

    @Test
    fun `beans 返回所有匹配类型的 Bean`() {
        BeanContainer.resetForTesting()
        val bean1 = ExtTestBean()
        val bean2 = ExtTestBean()
        BeanContainer.registerBean("ext1", bean1)
        BeanContainer.registerBean("ext2", bean2)
        setInitialized(true)
        try {
            val result = beans<ExtTestBean>()
            assertEquals(2, result.size)
        } finally {
            BeanContainer.resetForTesting()
        }
    }

    // 通过反射设置 initialized 字段
    private fun setInitialized(value: Boolean) {
        val field = BeanContainer::class.java.getDeclaredField("initialized")
        field.isAccessible = true
        field.set(BeanContainer, value)
    }

    class ExtTestBean
}

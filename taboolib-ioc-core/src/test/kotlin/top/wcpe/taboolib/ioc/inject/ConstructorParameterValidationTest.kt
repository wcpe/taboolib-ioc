package top.wcpe.taboolib.ioc.inject

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import top.wcpe.taboolib.ioc.IocTestContext
import top.wcpe.taboolib.ioc.annotation.*

/**
 * 构造函数参数验证测试
 * 
 * 测试当依赖 Bean 不存在时，是否能抛出清晰的异常信息
 */
class ConstructorParameterValidationTest {

    @Test
    fun `单参数构造函数 - 依赖不存在时抛出异常`() {
        val ctx = IocTestContext()
        ctx.register(BeanWithMissingDependency::class.java)

        val exception = assertThrows(BeanInstantiationException::class.java) {
            ctx.initialize()
        }

        // 验证异常信息包含必要的调试信息
        assertTrue(exception.message!!.contains("beanWithMissingDependency"))
        assertTrue(exception.message!!.contains("NonExistentService"))
        assertTrue(exception.message!!.contains("参数 #0"))
        assertTrue(exception.message!!.contains("找不到匹配的 Bean"))
    }

    @Test
    fun `多参数构造函数 - 第一个参数缺失`() {
        val ctx = IocTestContext()
        ctx.register(ExistingDep::class.java)
        ctx.register(BeanWithMultipleParams::class.java)

        val exception = assertThrows(BeanInstantiationException::class.java) {
            ctx.initialize()
        }

        // 验证异常指向第一个参数
        assertTrue(exception.message!!.contains("参数 #0"))
        assertTrue(exception.message!!.contains("MissingDepA"))
    }

    @Test
    fun `多参数构造函数 - 第二个参数缺失`() {
        val ctx = IocTestContext()
        ctx.register(ExistingDepA::class.java)
        ctx.register(BeanWithSecondParamMissing::class.java)

        val exception = assertThrows(BeanInstantiationException::class.java) {
            ctx.initialize()
        }

        // 验证异常指向第二个参数
        assertTrue(exception.message!!.contains("参数 #1"))
        assertTrue(exception.message!!.contains("MissingDepB"))
    }

    @Test
    fun `带 Named 限定符的参数 - 名称不匹配时抛出异常`() {
        val ctx = IocTestContext()
        ctx.register(NamedDepX::class.java) // 注册名称为 "depX" 的 Bean
        ctx.register(BeanWithNamedParam::class.java) // 需要名称为 "depY" 的 Bean

        val exception = assertThrows(BeanInstantiationException::class.java) {
            ctx.initialize()
        }

        // 验证异常信息包含名称限定符
        assertTrue(exception.message!!.contains("名称限定符: 'depY'"))
        assertTrue(exception.message!!.contains("NamedDependency"))
    }

    @Test
    fun `带 Named 限定符的参数 - 名称匹配时成功创建`() {
        val ctx = IocTestContext()
        ctx.register(NamedDepY::class.java) // 注册名称为 "depY" 的 Bean
        ctx.register(BeanWithNamedParam::class.java)
        ctx.initialize()

        val bean = ctx.getBean(BeanWithNamedParam::class.java)
        assertNotNull(bean)
        assertNotNull(bean!!.dep)
    }

    @Test
    fun `异常消息包含建议信息`() {
        val ctx = IocTestContext()
        ctx.register(BeanWithMissingDependency::class.java)

        val exception = assertThrows(BeanInstantiationException::class.java) {
            ctx.initialize()
        }

        // 验证异常消息包含有用的建议
        assertTrue(exception.message!!.contains("建议"))
        assertTrue(exception.message!!.contains("@Component"))
        assertTrue(exception.message!!.contains("@Lazy"))
    }

    @Test
    fun `Lazy 参数不会触发验证`() {
        val ctx = IocTestContext()
        ctx.register(BeanWithLazyParam::class.java)
        
        // 不应抛出异常，因为 Lazy 参数允许为 null
        assertDoesNotThrow {
            ctx.initialize()
        }

        val bean = ctx.getBean(BeanWithLazyParam::class.java)
        assertNotNull(bean)
        assertNotNull(bean!!.lazyDep) // Lazy 代理应该被创建
    }

    @Test
    fun `工厂方法参数缺失时抛出异常`() {
        val ctx = IocTestContext()
        ctx.register(ConfigWithMissingDep::class.java)

        val exception = assertThrows(BeanInstantiationException::class.java) {
            ctx.initialize()
        }

        // 验证异常信息包含工厂方法信息
        assertTrue(exception.message!!.contains("@Bean 工厂方法"))
        assertTrue(exception.message!!.contains("createBean"))
        assertTrue(exception.message!!.contains("MissingFactoryDep"))
    }

    @Test
    fun `工厂方法参数存在时成功创建`() {
        val ctx = IocTestContext()
        ctx.register(FactoryDep::class.java)
        ctx.register(ConfigWithFactoryDep::class.java)
        ctx.initialize()

        val bean = ctx.getBean(FactoryProduct::class.java)
        assertNotNull(bean)
        assertNotNull(bean!!.dep)
    }

    // ==================== 测试用 Bean ====================

    interface NonExistentService

    @Service
    class BeanWithMissingDependency(val dep: NonExistentService)

    @Component
    class ExistingDep

    interface MissingDepA
    
    @Service
    class BeanWithMultipleParams(
        val depA: MissingDepA,
        val depB: ExistingDep
    )

    @Component
    class ExistingDepA

    interface MissingDepB

    @Service
    class BeanWithSecondParamMissing(
        val depA: ExistingDepA,
        val depB: MissingDepB
    )

    interface NamedDependency

    @Component("depX")
    class NamedDepX : NamedDependency

    @Component("depY")
    class NamedDepY : NamedDependency

    @Service
    class BeanWithNamedParam @Inject constructor(
        @Named("depY") val dep: NamedDependency
    )

    interface LazyDependency

    @Service
    class BeanWithLazyParam @Inject constructor(
        @Lazy val lazyDep: LazyDependency
    )

    interface MissingFactoryDep

    @Configuration
    class ConfigWithMissingDep {
        @Bean
        fun createBean(dep: MissingFactoryDep): FactoryProduct {
            return FactoryProduct(null)
        }
    }

    @Component
    class FactoryDep

    @Configuration
    class ConfigWithFactoryDep {
        @Bean
        fun createBean(dep: FactoryDep): FactoryProduct {
            return FactoryProduct(dep)
        }
    }

    data class FactoryProduct(val dep: FactoryDep?)
}

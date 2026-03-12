package top.wcpe.taboolib.ioc.condition

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import top.wcpe.taboolib.ioc.IocTestContext
import top.wcpe.taboolib.ioc.annotation.*

class ConditionEvaluatorTest {

    @Test
    fun `ConditionalOnClass should register when class exists`() {
        val ctx = IocTestContext()
        assertTrue(ctx.registerWithCondition(ClassPresentBean::class.java))
    }

    @Test
    fun `ConditionalOnClass should skip when class is absent`() {
        val ctx = IocTestContext()
        assertFalse(ctx.registerWithCondition(ClassAbsentBean::class.java))
    }

    @Test
    fun `ConditionalOnMissingClass should register when class is absent`() {
        val ctx = IocTestContext()
        assertTrue(ctx.registerWithCondition(MissingClassMatchBean::class.java))
    }

    @Test
    fun `ConditionalOnMissingClass should skip when class exists`() {
        val ctx = IocTestContext()
        assertFalse(ctx.registerWithCondition(MissingClassNoMatchBean::class.java))
    }

    @Test
    fun `ConditionalOnProperty should register when property matches`() {
        System.setProperty("ioc.test.enabled", "true")
        try {
            val ctx = IocTestContext()
            assertTrue(ctx.registerWithCondition(PropertyMatchBean::class.java))
        } finally {
            System.clearProperty("ioc.test.enabled")
        }
    }

    @Test
    fun `ConditionalOnProperty should register when missing and matchIfMissing is true`() {
        val ctx = IocTestContext()
        assertTrue(ctx.registerWithCondition(PropertyMissingAllowedBean::class.java))
    }

    @Test
    fun `ConditionalOnProperty should skip when missing and matchIfMissing is false`() {
        val ctx = IocTestContext()
        assertFalse(ctx.registerWithCondition(PropertyMissingDeniedBean::class.java))
    }

    @Test
    fun `ConditionalOnBean should register when bean type exists`() {
        val ctx = IocTestContext()
        ctx.register(DummyServiceImpl::class.java)
        assertTrue(ctx.registerWithCondition(DependsOnDummyBean::class.java))
    }

    @Test
    fun `ConditionalOnBean should skip when bean name is missing`() {
        val ctx = IocTestContext()
        assertFalse(ctx.registerWithCondition(DependsOnMissingNameBean::class.java))
    }

    @Test
    fun `ConditionalOnMissingBean should skip when bean type exists`() {
        val ctx = IocTestContext()
        ctx.register(DummyServiceImpl::class.java)
        assertFalse(ctx.registerWithCondition(FallbackWhenNoDummyBean::class.java))
    }

    @Test
    fun `ConditionalOnMissingBean should register when bean name is absent`() {
        val ctx = IocTestContext()
        assertTrue(ctx.registerWithCondition(FallbackWhenNoNameBean::class.java))
    }

    @Test
    fun `Conditional with custom true condition should register`() {
        val ctx = IocTestContext()
        assertTrue(ctx.registerWithCondition(CustomConditionTrueBean::class.java))
    }

    @Test
    fun `Conditional with custom false condition should skip`() {
        val ctx = IocTestContext()
        assertFalse(ctx.registerWithCondition(CustomConditionFalseBean::class.java))
    }

    // ── 1. 多条件组合 ──

    @Test
    fun `MultiCondition should register when both class and property match`() {
        System.setProperty("ioc.multi.test", "yes")
        try {
            val ctx = IocTestContext()
            assertTrue(ctx.registerWithCondition(MultiConditionBean::class.java))
        } finally {
            System.clearProperty("ioc.multi.test")
        }
    }

    @Test
    fun `MultiCondition should skip when property does not match even though class exists`() {
        System.setProperty("ioc.multi.test", "no")
        try {
            val ctx = IocTestContext()
            assertFalse(ctx.registerWithCondition(MultiConditionBean::class.java))
        } finally {
            System.clearProperty("ioc.multi.test")
        }
    }

    @Test
    fun `MultiCondition should skip when property is missing`() {
        // 确保属性不存在
        System.clearProperty("ioc.multi.test")
        val ctx = IocTestContext()
        assertFalse(ctx.registerWithCondition(MultiConditionBean::class.java))
    }

    // ── 2. @Conditional 多条件类（AND 语义） ──

    @Test
    fun `Conditional with multiple conditions should skip when one is false (AND semantics)`() {
        val ctx = IocTestContext()
        assertFalse(ctx.registerWithCondition(MultiCustomConditionBean::class.java))
    }

    // ── 3. @ConditionalOnProperty 边界情况 ──

    @Test
    fun `ConditionalOnProperty with empty havingValue should register when property exists`() {
        System.setProperty("ioc.existence.check", "anything")
        try {
            val ctx = IocTestContext()
            assertTrue(ctx.registerWithCondition(PropertyExistenceOnlyBean::class.java))
        } finally {
            System.clearProperty("ioc.existence.check")
        }
    }

    @Test
    fun `ConditionalOnProperty should skip when property value does not match havingValue`() {
        System.setProperty("ioc.strict.value", "wrong")
        try {
            val ctx = IocTestContext()
            assertFalse(ctx.registerWithCondition(PropertyStrictValueBean::class.java))
        } finally {
            System.clearProperty("ioc.strict.value")
        }
    }

    @Test
    fun `ConditionalOnProperty should register when property value matches havingValue`() {
        System.setProperty("ioc.strict.value", "expected")
        try {
            val ctx = IocTestContext()
            assertTrue(ctx.registerWithCondition(PropertyStrictValueBean::class.java))
        } finally {
            System.clearProperty("ioc.strict.value")
        }
    }

    // ── 4. @ConditionalOnBean 同时指定 type 和 name ──

    @Test
    fun `ConditionalOnBean with both type and name should register when both exist`() {
        val ctx = IocTestContext()
        ctx.register(DummyServiceImpl::class.java)
        assertTrue(ctx.registerWithCondition(BothTypeAndNameBean::class.java))
    }

    @Test
    fun `ConditionalOnBean with both type and name should skip when type exists but name does not`() {
        val ctx = IocTestContext()
        ctx.register(DummyServiceImpl::class.java)
        assertFalse(ctx.registerWithCondition(TypeExistsButNameMissingBean::class.java))
    }

    // ── 5. 端到端集成测试 ──

    @Test
    fun `integration - ConditionalOnBean bean should be created when dependency is registered`() {
        val ctx = IocTestContext()
        ctx.register(DummyServiceImpl::class.java)
        ctx.register(ConditionalDependentBean::class.java)
        ctx.initialize()

        val bean = ctx.getBean(ConditionalDependentBean::class.java)
        assertNotNull(bean, "ConditionalOnBean 的 Bean 应在依赖存在时被创建")
    }

    @Test
    fun `integration - ConditionalOnMissingBean fallback should be created when dependency is absent`() {
        val ctx = IocTestContext()
        // 不注册 DummyServiceImpl，fallback 应该生效
        assertTrue(ctx.registerWithCondition(FallbackServiceBean::class.java))
        ctx.initialize()

        val bean = ctx.getBean(FallbackServiceBean::class.java)
        assertNotNull(bean, "ConditionalOnMissingBean 的 fallback Bean 应在依赖缺失时被创建")
    }

    @Test
    fun `integration - ConditionalOnMissingBean fallback should NOT be created when dependency exists`() {
        val ctx = IocTestContext()
        ctx.register(DummyServiceImpl::class.java)
        assertFalse(ctx.registerWithCondition(FallbackServiceBean::class.java))
    }
}

// ── 测试用组件 ──

@Component
@ConditionalOnClass("java.lang.String")
class ClassPresentBean

@Component
@ConditionalOnClass("com.nonexistent.FakeClass")
class ClassAbsentBean

@Component
@ConditionalOnMissingClass("com.nonexistent.FakeClass")
class MissingClassMatchBean

@Component
@ConditionalOnMissingClass("java.lang.String")
class MissingClassNoMatchBean

@Component
@ConditionalOnProperty(name = "ioc.test.enabled", havingValue = "true")
class PropertyMatchBean

@Component
@ConditionalOnProperty(name = "ioc.test.missing", matchIfMissing = true)
class PropertyMissingAllowedBean

@Component
@ConditionalOnProperty(name = "ioc.test.missing", matchIfMissing = false)
class PropertyMissingDeniedBean

interface DummyService
@Component
class DummyServiceImpl : DummyService

@Component
@ConditionalOnBean(DummyService::class)
class DependsOnDummyBean

@Component
@ConditionalOnBean(name = ["nonExistentBean"])
class DependsOnMissingNameBean

@Component
@ConditionalOnMissingBean(DummyService::class)
class FallbackWhenNoDummyBean

@Component
@ConditionalOnMissingBean(name = ["nonExistentBean"])
class FallbackWhenNoNameBean

class AlwaysTrueCondition : Condition {
    override fun matches(context: ConditionContext): Boolean = true
}
class AlwaysFalseCondition : Condition {
    override fun matches(context: ConditionContext): Boolean = false
}

@Component
@Conditional(AlwaysTrueCondition::class)
class CustomConditionTrueBean

@Component
@Conditional(AlwaysFalseCondition::class)
class CustomConditionFalseBean

// ── 1. 多条件组合测试用组件 ──

@Component
@ConditionalOnClass("java.lang.String")
@ConditionalOnProperty(name = "ioc.multi.test", havingValue = "yes")
class MultiConditionBean

// ── 2. @Conditional 多条件类（AND 语义）测试用组件 ──

@Component
@Conditional(AlwaysTrueCondition::class, AlwaysFalseCondition::class)
class MultiCustomConditionBean

// ── 3. @ConditionalOnProperty 边界情况测试用组件 ──

@Component
@ConditionalOnProperty(name = "ioc.existence.check")
class PropertyExistenceOnlyBean

@Component
@ConditionalOnProperty(name = "ioc.strict.value", havingValue = "expected")
class PropertyStrictValueBean

// ── 4. @ConditionalOnBean 同时指定 type 和 name 测试用组件 ──

@Component
@ConditionalOnBean(DummyService::class, name = ["dummyServiceImpl"])
class BothTypeAndNameBean

@Component
@ConditionalOnBean(DummyService::class, name = ["nonExistentServiceName"])
class TypeExistsButNameMissingBean

// ── 5. 端到端集成测试用组件 ──

@Component
@ConditionalOnBean(DummyService::class)
class ConditionalDependentBean

@Component
@ConditionalOnMissingBean(DummyService::class)
class FallbackServiceBean

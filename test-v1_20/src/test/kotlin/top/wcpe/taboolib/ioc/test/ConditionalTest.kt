package top.wcpe.taboolib.ioc.test

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import top.wcpe.taboolib.ioc.IocTestContext
import top.wcpe.taboolib.ioc.annotation.*

/**
 * @Conditional* 系列注解
 */
class ConditionalTest {

    @BeforeEach
    fun cleanup() {
        System.clearProperty("ioc.test.prop")
        System.clearProperty("ioc.test.flag")
    }

    @AfterEach
    fun tearDown() {
        System.clearProperty("ioc.test.prop")
        System.clearProperty("ioc.test.flag")
    }

    @Test
    fun `onBean should pass when target bean exists`() {
        val ctx = IocTestContext()
        ctx.register(CT_Base::class.java)
        val ok = ctx.registerWithCondition(CT_OnBeanPresent::class.java)
        assertTrue(ok)
    }

    @Test
    fun `onBean should fail when target bean missing`() {
        val ctx = IocTestContext()
        val ok = ctx.registerWithCondition(CT_OnBeanPresent::class.java)
        assertFalse(ok)
    }

    @Test
    fun `onMissingBean should pass when target not present`() {
        val ctx = IocTestContext()
        val ok = ctx.registerWithCondition(CT_OnMissingBean::class.java)
        assertTrue(ok)
    }

    @Test
    fun `onMissingBean should fail when target present`() {
        val ctx = IocTestContext()
        ctx.register(CT_Base::class.java)
        val ok = ctx.registerWithCondition(CT_OnMissingBean::class.java)
        assertFalse(ok)
    }

    @Test
    fun `onClass should pass when class exists`() {
        val ctx = IocTestContext()
        assertTrue(ctx.registerWithCondition(CT_OnClassPresent::class.java))
    }

    @Test
    fun `onClass should fail when class missing`() {
        val ctx = IocTestContext()
        assertFalse(ctx.registerWithCondition(CT_OnClassMissing::class.java))
    }

    @Test
    fun `onMissingClass should pass when class absent`() {
        val ctx = IocTestContext()
        assertTrue(ctx.registerWithCondition(CT_OnMissingClassPresent::class.java))
    }

    @Test
    fun `onMissingClass should fail when class present`() {
        val ctx = IocTestContext()
        assertFalse(ctx.registerWithCondition(CT_OnMissingClassFails::class.java))
    }

    @Test
    fun `onProperty should pass when value matches`() {
        System.setProperty("ioc.test.prop", "ok")
        val ctx = IocTestContext()
        assertTrue(ctx.registerWithCondition(CT_OnProperty::class.java))
    }

    @Test
    fun `onProperty should fail when value mismatch`() {
        System.setProperty("ioc.test.prop", "bad")
        val ctx = IocTestContext()
        assertFalse(ctx.registerWithCondition(CT_OnProperty::class.java))
    }

    @Test
    fun `onProperty matchIfMissing true should pass when unset`() {
        val ctx = IocTestContext()
        assertTrue(ctx.registerWithCondition(CT_OnPropertyMatchMissing::class.java))
    }

    @Test
    fun `onProperty matchIfMissing false should fail when unset`() {
        val ctx = IocTestContext()
        assertFalse(ctx.registerWithCondition(CT_OnProperty::class.java))
    }

    @Test
    fun `custom Conditional should pass when matches returns true`() {
        System.setProperty("ioc.test.flag", "yes")
        val ctx = IocTestContext()
        assertTrue(ctx.registerWithCondition(CT_WithCustom::class.java))
    }

    @Test
    fun `custom Conditional should fail when matches returns false`() {
        val ctx = IocTestContext()
        assertFalse(ctx.registerWithCondition(CT_WithCustom::class.java))
    }
}

@Component
class CT_Base

@Component
@ConditionalOnBean(CT_Base::class)
class CT_OnBeanPresent

@Component
@ConditionalOnMissingBean(CT_Base::class)
class CT_OnMissingBean

@Component
@ConditionalOnClass("java.lang.String")
class CT_OnClassPresent

@Component
@ConditionalOnClass("foo.bar.NotExist")
class CT_OnClassMissing

@Component
@ConditionalOnMissingClass("foo.bar.NotExist")
class CT_OnMissingClassPresent

@Component
@ConditionalOnMissingClass("java.lang.String")
class CT_OnMissingClassFails

@Component
@ConditionalOnProperty(name = "ioc.test.prop", havingValue = "ok")
class CT_OnProperty

@Component
@ConditionalOnProperty(name = "ioc.test.prop.absent", havingValue = "ok", matchIfMissing = true)
class CT_OnPropertyMatchMissing

class CT_FlagCondition : Condition {
    override fun matches(context: ConditionContext): Boolean {
        return System.getProperty("ioc.test.flag") == "yes"
    }
}

@Component
@Conditional(CT_FlagCondition::class)
class CT_WithCustom

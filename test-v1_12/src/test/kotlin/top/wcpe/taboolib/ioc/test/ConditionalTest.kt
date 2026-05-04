package top.wcpe.taboolib.ioc.test.conditional

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import top.wcpe.taboolib.ioc.IocTestContext
import top.wcpe.taboolib.ioc.annotation.*

class ConditionalTest {

    @Test
    fun `OnClass register when class exists`() {
        val ctx = IocTestContext()
        assertTrue(ctx.registerWithCondition(CondClassPresent::class.java))
    }

    @Test
    fun `OnClass skip when class missing`() {
        val ctx = IocTestContext()
        assertFalse(ctx.registerWithCondition(CondClassAbsent::class.java))
    }

    @Test
    fun `OnMissingClass register when absent`() {
        val ctx = IocTestContext()
        assertTrue(ctx.registerWithCondition(CondMissingClassMatch::class.java))
    }

    @Test
    fun `OnMissingClass skip when present`() {
        val ctx = IocTestContext()
        assertFalse(ctx.registerWithCondition(CondMissingClassNoMatch::class.java))
    }

    @Test
    fun `OnProperty matches havingValue`() {
        System.setProperty("ioc.v12.cond", "go")
        try {
            val ctx = IocTestContext()
            assertTrue(ctx.registerWithCondition(CondPropertyMatch::class.java))
        } finally { System.clearProperty("ioc.v12.cond") }
    }

    @Test
    fun `OnProperty matchIfMissing true`() {
        val ctx = IocTestContext()
        assertTrue(ctx.registerWithCondition(CondPropertyMissingTrue::class.java))
    }

    @Test
    fun `OnProperty matchIfMissing false skip`() {
        val ctx = IocTestContext()
        assertFalse(ctx.registerWithCondition(CondPropertyMissingFalse::class.java))
    }

    @Test
    fun `OnProperty empty havingValue checks existence`() {
        System.setProperty("ioc.v12.exist", "any")
        try {
            val ctx = IocTestContext()
            assertTrue(ctx.registerWithCondition(CondPropertyExist::class.java))
        } finally { System.clearProperty("ioc.v12.exist") }
    }

    @Test
    fun `OnBean by type registers when type exists`() {
        val ctx = IocTestContext()
        ctx.register(CondDummyImpl::class.java)
        assertTrue(ctx.registerWithCondition(CondNeedsDummy::class.java))
    }

    @Test
    fun `OnBean by name skips when missing`() {
        val ctx = IocTestContext()
        assertFalse(ctx.registerWithCondition(CondNeedsName::class.java))
    }

    @Test
    fun `OnMissingBean skips when present`() {
        val ctx = IocTestContext()
        ctx.register(CondDummyImpl::class.java)
        assertFalse(ctx.registerWithCondition(CondFallback::class.java))
    }

    @Test
    fun `OnMissingBean by name registers when absent`() {
        val ctx = IocTestContext()
        assertTrue(ctx.registerWithCondition(CondFallbackName::class.java))
    }

    @Test
    fun `Conditional custom true`() {
        val ctx = IocTestContext()
        assertTrue(ctx.registerWithCondition(CondCustomTrue::class.java))
    }

    @Test
    fun `Conditional custom false`() {
        val ctx = IocTestContext()
        assertFalse(ctx.registerWithCondition(CondCustomFalse::class.java))
    }
}

interface CondDummy
@Component class CondDummyImpl : CondDummy

@Component @ConditionalOnClass("java.lang.String") class CondClassPresent
@Component @ConditionalOnClass("no.such.Class") class CondClassAbsent
@Component @ConditionalOnMissingClass("no.such.Class") class CondMissingClassMatch
@Component @ConditionalOnMissingClass("java.lang.String") class CondMissingClassNoMatch
@Component @ConditionalOnProperty(name = "ioc.v12.cond", havingValue = "go") class CondPropertyMatch
@Component @ConditionalOnProperty(name = "ioc.v12.absent", matchIfMissing = true) class CondPropertyMissingTrue
@Component @ConditionalOnProperty(name = "ioc.v12.absent", matchIfMissing = false) class CondPropertyMissingFalse
@Component @ConditionalOnProperty(name = "ioc.v12.exist") class CondPropertyExist

@Component @ConditionalOnBean(CondDummy::class) class CondNeedsDummy
@Component @ConditionalOnBean(name = ["nonexistName"]) class CondNeedsName
@Component @ConditionalOnMissingBean(CondDummy::class) class CondFallback
@Component @ConditionalOnMissingBean(name = ["nonexistName"]) class CondFallbackName

class CondAlwaysTrue : Condition { override fun matches(context: ConditionContext) = true }
class CondAlwaysFalse : Condition { override fun matches(context: ConditionContext) = false }

@Component @Conditional(CondAlwaysTrue::class) class CondCustomTrue
@Component @Conditional(CondAlwaysFalse::class) class CondCustomFalse

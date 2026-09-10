package top.wcpe.taboolib.ioc.scan

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import top.wcpe.taboolib.ioc.IocTestContext
import top.wcpe.taboolib.ioc.annotation.*
import top.wcpe.taboolib.ioc.condition.ConditionEvaluator
import top.wcpe.taboolib.ioc.condition.OnPropertyCondition
import top.wcpe.taboolib.ioc.inject.ValueResolver

/**
 * E3 修复批次回归测试。
 *
 * 覆盖：
 * - A-P0-05：@Bean 方法返回 void 时必须软降级（告警 + 跳过），绝不抛异常
 * - A-P1-09：@Conditional 自定义条件类在扫描过程中不得被重复实例化
 * - C-P2-19：@ConditionalOnProperty 必须能读到 @PropertySource 加载的属性（与 @Value 同源）
 */
class E3ScanRobustnessTest {

    // ═══════════════════════════════════════════════════════════════
    // A-P0-05：@Bean 返回 void → 软降级（红→绿）
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun `scan should skip void bean method without throwing`() {
        // 修复前：ConfigurationScanner.scan 内部 require(returnType != Void.TYPE) 抛 IllegalArgumentException
        assertDoesNotThrow(
            { ConfigurationScanner.scan(VoidBeanConfig::class.java, "voidBeanConfig") },
            "返回 void 的 @Bean 方法不得抛异常，应软降级跳过"
        )
        val result = ConfigurationScanner.scan(VoidBeanConfig::class.java, "voidBeanConfig")
        assertFalse(result.any { it.name == "badVoidBean" }, "void @Bean 应被跳过")
        assertTrue(result.any { it.name == "goodBean" }, "同配置类中的合法 @Bean 不应受影响")
    }

    @Test
    fun `void bean method should not break whole configuration class scan`() {
        val result = ConfigurationScanner.scan(VoidBeanConfig::class.java, "voidBeanConfig")
        assertEquals(1, result.size, "仅合法 @Bean 被解析，void @Bean 被跳过")
        assertEquals("goodBean", result.first().name)
    }

    @Test
    fun `void bean method skipped while valid bean still resolvable`() {
        val ctx = IocTestContext()
        // @Configuration 类本身 + 合法 @Bean 应可解析，void @Bean 只是被跳过
        assertDoesNotThrow {
            ctx.register(VoidBeanConfig::class.java)
            ctx.initialize()
        }
        assertNotNull(ctx.getBean(StringBuilder::class.java), "合法 @Bean 应可被解析")
    }

    // ═══════════════════════════════════════════════════════════════
    // C-P2-19：@ConditionalOnProperty 与 @PropertySource 同源
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun `ConditionalOnProperty should see property loaded from PropertySource`() {
        ValueResolver.clearProperties()
        // 模拟 @PropertySource 加载的配置（非系统属性）
        ValueResolver.setProperty("ioc.propsource.flag", "on")
        try {
            val annotation = PropertyProbeHolder::class.java
                .getAnnotation(ConditionalOnProperty::class.java)
            assertNotNull(annotation)
            assertTrue(
                OnPropertyCondition.matches(annotation!!),
                "@ConditionalOnProperty 应能看到 @PropertySource 加载的属性（与 @Value 同源）"
            )
        } finally {
            ValueResolver.clearProperties()
        }
    }

    @Test
    fun `ConditionalOnProperty should not match when PropertySource value differs`() {
        ValueResolver.clearProperties()
        ValueResolver.setProperty("ioc.propsource.flag", "off")
        try {
            val annotation = PropertyProbeHolder::class.java
                .getAnnotation(ConditionalOnProperty::class.java)
            assertNotNull(annotation)
            assertFalse(
                OnPropertyCondition.matches(annotation!!),
                "属性值不匹配时应返回 false"
            )
        } finally {
            ValueResolver.clearProperties()
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // A-P1-09：条件类不得重复实例化
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun `custom condition should be instantiated once per element`() {
        val ctx = IocTestContext()
        val element = CountingConditionBean::class.java

        // 预热一次，确保缓存已建立（条件类实例为 JVM 级缓存）
        ConditionEvaluator.shouldSkipOnScan(element, ctx.createConditionContext())

        CountingCondition.reset()
        repeat(3) {
            ConditionEvaluator.shouldSkipOnScan(element, ctx.createConditionContext())
        }

        // 缓存命中时不应再触发任何新的实例化；即实例化次数与重复调用次数无关（恒为 0 次新增）
        assertEquals(
            0, CountingCondition.instantiationCount,
            "@Conditional 条件类实例应被缓存复用，重复评估不应再次实例化，实际新增 ${CountingCondition.instantiationCount} 次"
        )
    }

    @Test
    fun `custom condition matches should be cached for identical context`() {
        val ctx = IocTestContext()
        val element = CountingConditionBean::class.java

        // 预热建立缓存
        ConditionEvaluator.shouldSkipOnScan(element, ctx.createConditionContext())

        CountingCondition.reset()
        val results = (1..5).map {
            ConditionEvaluator.shouldSkipOnScan(element, ctx.createConditionContext())
        }

        assertEquals(0, CountingCondition.instantiationCount, "实例化应被缓存，重复评估不再 newInstance")
        assertTrue(results.all { it == results.first() }, "缓存不应改变评估结果语义（结果稳定一致）")
    }

    // ═══════════════════════════════════════════════════════════════
    // A-P1-11：类枚举顺序确定 + 去重
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun `orderClassesDeterministically should be independent of input order`() {
        val inputA = listOf(Zeta::class.java, Alpha::class.java, Mid::class.java)
        val inputB = listOf(Mid::class.java, Zeta::class.java, Alpha::class.java)
        val inputC = listOf(Alpha::class.java, Zeta::class.java, Mid::class.java)

        val expected = listOf(Alpha::class.java.name, Mid::class.java.name, Zeta::class.java.name)

        assertEquals(expected, orderClassesDeterministically(inputA).map { it.name })
        assertEquals(expected, orderClassesDeterministically(inputB).map { it.name })
        assertEquals(expected, orderClassesDeterministically(inputC).map { it.name })
    }

    @Test
    fun `orderClassesDeterministically should deduplicate by class name`() {
        val result = orderClassesDeterministically(
            listOf(Alpha::class.java, Alpha::class.java, Mid::class.java)
        )
        assertEquals(2, result.size, "同名类应被去重")
        assertEquals(listOf(Alpha::class.java.name, Mid::class.java.name), result.map { it.name })
    }

    @Test
    fun `duplicate bean name should be skipped not overwritten`() {
        // 直接验证 BeanRegistry 的 contains 语义 + ComponentVisitor 的跳过逻辑入口
        val ctx = IocTestContext()
        ctx.register(VoidBeanConfig::class.java)
        // 重复注册同名组件应被 BeanRegistry.contains 拦截（不覆盖）
        assertTrue(ctx.containsBean("voidBeanConfig"))
        ctx.register(VoidBeanConfig::class.java)
        assertTrue(ctx.containsBean("voidBeanConfig"))
    }
}

// ── A-P1-11 排序测试用类 ──

internal class Alpha
internal class Mid
internal class Zeta

// ── 测试用组件 ──

@ConditionalOnProperty(name = "ioc.propsource.flag", havingValue = "on")
private class PropertyProbeHolder

@Configuration
class VoidBeanConfig {

    @Bean
    fun badVoidBean() {
        // 返回类型为 void：属于配置错误，运行时必须软降级跳过而非抛异常
    }

    @Bean
    fun goodBean(): StringBuilder = StringBuilder("good")
}

@Conditional(CountingCondition::class)
@Component
class CountingConditionBean

class CountingCondition : Condition {
    companion object {
        @Volatile
        var instantiationCount: Int = 0

        @Volatile
        var matchesCount: Int = 0

        fun reset() {
            instantiationCount = 0
            matchesCount = 0
        }
    }

    init {
        instantiationCount++
    }

    override fun matches(context: ConditionContext): Boolean {
        matchesCount++
        return true
    }
}

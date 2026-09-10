package top.wcpe.taboolib.ioc.inject

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import top.wcpe.taboolib.ioc.IocTestContext
import top.wcpe.taboolib.ioc.annotation.Aspect
import top.wcpe.taboolib.ioc.annotation.Bean
import top.wcpe.taboolib.ioc.annotation.Before
import top.wcpe.taboolib.ioc.annotation.Component
import top.wcpe.taboolib.ioc.annotation.Configuration
import top.wcpe.taboolib.ioc.annotation.Inject
import top.wcpe.taboolib.ioc.annotation.Lazy
import top.wcpe.taboolib.ioc.annotation.Named
import top.wcpe.taboolib.ioc.annotation.PostEnable
import top.wcpe.taboolib.ioc.annotation.PreDestroy
import top.wcpe.taboolib.ioc.annotation.Service
import top.wcpe.taboolib.ioc.annotation.Value

/**
 * E2 注入链修复的回归测试。
 *
 * 覆盖：
 * - A-P0-03：@Lazy 具体类字段回退立即注入时，缺失依赖必须遵循 required 语义
 * - A-P0-04：@Lazy 具体类字段目标被 AOP 代理时，给出明确指引而非 BeanNotOfRequiredTypeException
 * - A-P1-06：@Bean 返回接口类型时补充扫描 @PostEnable/@PreDestroy
 * - A-P1-08：方法注入的 required 语义
 * - C-P2-08：@Value 非法布尔字面量告警（行为不变，仍返回 false）
 */
class E2InjectionChainFixTest {

    // ==================== A-P0-03 ====================

    /**
     * @Lazy + 具体类 + 依赖缺失 + required=true（默认）→ 修复前静默通过（红），
     * 修复后必须抛异常。
     */
    @Test
    fun `A-P0-03 Lazy 具体类字段依赖缺失 required 时抛异常`() {
        val ctx = IocTestContext()
        ctx.register(LazyConcreteRequiredConsumer::class.java)
        // 故意不注册 LazyConcreteDep

        // eager singleton 在 initialize 期即被装配，缺失 required 依赖应在此抛异常
        val ex = assertThrows(IllegalStateException::class.java) {
            ctx.initialize()
        }
        assertTrue(
            ex.message?.contains("LazyConcreteDep") == true || ex.message?.contains("@Lazy") == true,
            "异常信息应指向缺失的依赖或 @Lazy 用法，实际: ${ex.message}"
        )
    }

    /**
     * @Lazy + 具体类 + 依赖缺失 + required=false → 不抛异常，字段保持 null。
     */
    @Test
    fun `A-P0-03 Lazy 具体类字段依赖缺失非 required 时不抛异常`() {
        val ctx = IocTestContext()
        ctx.register(LazyConcreteOptionalConsumer::class.java)
        ctx.initialize()

        val consumer = ctx.getBean(LazyConcreteOptionalConsumer::class.java)
        assertNotNull(consumer)
        assertNull(consumer!!.dep, "非 required 缺失依赖时字段应保持 null")
    }

    /**
     * @Lazy + 具体类 + 依赖存在 → 回退立即注入成功，字段被赋值。
     */
    @Test
    fun `A-P0-03 Lazy 具体类字段依赖存在时回退立即注入成功`() {
        val ctx = IocTestContext()
        ctx.register(LazyConcreteDep::class.java)
        ctx.register(LazyConcreteRequiredConsumer::class.java)
        ctx.initialize()

        val consumer = ctx.getBean(LazyConcreteRequiredConsumer::class.java)
        assertNotNull(consumer)
        assertNotNull(consumer!!.dep, "依赖存在时回退立即注入应成功")
    }

    // ==================== A-P0-04 ====================

    /**
     * @Lazy + 具体类 + 目标被 AOP 代理 → 修复前抛 BeanNotOfRequiredTypeException（红），
     * 修复后抛出带 @Lazy 修复指引的异常，且消息指明「仅支持接口类型」。
     */
    @Test
    fun `A-P0-04 Lazy 具体类字段目标被 AOP 代理时给出明确指引`() {
        val ctx = IocTestContext()
        ctx.register(ConcreteAspect::class.java)
        ctx.register(ProxiedConcreteServiceImpl::class.java)
        ctx.register(LazyProxiedConcreteConsumer::class.java)

        val ex = assertThrows(IllegalStateException::class.java) {
            ctx.initialize()
        }
        val msg = ex.message ?: ""
        assertTrue(
            msg.contains("@Lazy") && msg.contains("接口"),
            "异常信息应给出 @Lazy 仅支持接口类型的修复指引，实际: $msg"
        )
    }

    // ==================== A-P1-06 ====================

    /**
     * @Bean 返回接口类型，实际产物类型带 @PostEnable → 必须被执行。
     */
    @Test
    fun `A-P1-06 Bean 返回接口类型时补充扫描 PostEnable`() {
        InterfacePostEnableProduct.postEnableInvoked = false

        val ctx = IocTestContext()
        ctx.register(PostEnableProductConfig::class.java)
        ctx.initialize()
        ctx.invokePostEnable()

        assertTrue(
            InterfacePostEnableProduct.postEnableInvoked,
            "@Bean 产物（接口返回类型）的 @PostEnable 应被执行"
        )
    }

    /**
     * @Bean 返回接口类型，实际产物类型带 @PreDestroy → 容器关闭时必须被执行。
     */
    @Test
    fun `A-P1-06 Bean 返回接口类型时补充扫描 PreDestroy`() {
        InterfacePreDestroyProduct.preDestroyInvoked = false

        val ctx = IocTestContext()
        ctx.register(PreDestroyProductConfig::class.java)
        ctx.initialize()
        ctx.shutdown()

        assertTrue(
            InterfacePreDestroyProduct.preDestroyInvoked,
            "@Bean 产物（接口返回类型）的 @PreDestroy 应被执行"
        )
    }

    // ==================== A-P1-08 ====================

    /**
     * 方法注入：@Inject(required=false) + 参数缺失 → 跳过方法调用，不抛异常。
     */
    @Test
    fun `A-P1-08 方法注入非 required 参数缺失时跳过`() {
        val ctx = IocTestContext()
        ctx.register(OptionalMethodInjectConsumer::class.java)
        ctx.initialize()

        val consumer = ctx.getBean(OptionalMethodInjectConsumer::class.java)
        assertNotNull(consumer)
        assertFalse(consumer!!.injected, "required=false 且参数缺失时方法应被跳过")
    }

    /**
     * 方法注入：@Inject（默认 required=true）+ 参数缺失 → 抛异常。
     */
    @Test
    fun `A-P1-08 方法注入 required 参数缺失时抛异常`() {
        val ctx = IocTestContext()
        ctx.register(RequiredMethodInjectConsumer::class.java)

        val ex = assertThrows(IllegalStateException::class.java) {
            ctx.initialize()
        }
        assertTrue(ex.message?.contains("方法注入") == true, "异常应来自方法注入校验，实际: ${ex.message}")
    }

    /**
     * 方法注入：依赖齐全 → 正常调用。
     */
    @Test
    fun `A-P1-08 方法注入依赖齐全时正常调用`() {
        val ctx = IocTestContext()
        ctx.register(MethodDep::class.java)
        ctx.register(RequiredMethodInjectConsumer::class.java)
        ctx.initialize()

        val consumer = ctx.getBean(RequiredMethodInjectConsumer::class.java)
        assertNotNull(consumer)
        assertTrue(consumer!!.injected)
    }

    // ==================== C-P2-08 ====================

    /**
     * @Value 非法布尔字面量 → 行为不变（返回 false），但不应抛异常。
     */
    @Test
    fun `C-P2-08 非法布尔字面量按 false 处理`() {
        ValueResolver.setProperty("e2.bad.bool", "ture")
        try {
            assertEquals(false, ValueResolver.resolve("\${e2.bad.bool}", Boolean::class.java))
        } finally {
            ValueResolver.clearProperties()
        }
    }

    /**
     * C-P2-08：char 不受支持 → 返回 null（保持现状，不新增支持）。
     */
    @Test
    fun `C-P2-08 char 类型保持不支持返回 null`() {
        ValueResolver.setProperty("e2.char", "x")
        try {
            assertNull(ValueResolver.resolve("\${e2.char}", Char::class.java))
            assertNull(ValueResolver.resolve("\${e2.char}", java.lang.Character::class.java))
        } finally {
            ValueResolver.clearProperties()
        }
    }

    /**
     * A-P1-07：混合文本表达式不解析、原样返回（保持 matchEntire 语义不变）。
     */
    @Test
    fun `A-P1-07 混合文本表达式原样返回不解析`() {
        ValueResolver.setProperty("e2.a", "A")
        ValueResolver.setProperty("e2.b", "B")
        try {
            assertEquals(
                "\${e2.a} - \${e2.b}",
                ValueResolver.resolve("\${e2.a} - \${e2.b}", String::class.java)
            )
        } finally {
            ValueResolver.clearProperties()
        }
    }

    // ==================== 构造函数确定性 + 回退 ====================

    /**
     * 判别性用例：类有**两个 @Inject 构造函数**，其中「参数更多」的那个依赖**不可解析**，
     * 另一个依赖可解析 → Bean 必须创建成功（回退到可解析的候选），而不是直接抛异常。
     *
     * 修复前（只取 `sorted.first()`）：首选是 2 参构造器，其依赖不可解析 → 扫描期即固化
     * 该构造器 → 实例化抛异常 → 用例红。
     * 修复后：实例化期按同一确定性顺序逐个尝试，回退到 1 参构造器 → 成功 → 用例绿。
     */
    @Test
    fun `构造函数 多 Inject 构造器时回退到可解析者`() {
        val ctx = IocTestContext()
        ctx.register(MultiInjectDepPresent::class.java)
        ctx.register(MultiInjectCtorBean::class.java)

        ctx.initialize()

        val bean = ctx.getBean(MultiInjectCtorBean::class.java)
        assertNotNull(bean, "应回退到可解析的 1 参构造器并成功创建 Bean")
        assertEquals("present", bean!!.which, "应选中依赖可解析的构造器")
    }

    // ==================== 测试用 Bean ====================

    @Component
    class MultiInjectDepPresent {
        fun label(): String = "present"
    }

    /** 故意不注册的依赖类型（构造器参数无法解析） */
    class MultiInjectDepMissing

    /** 故意不注册的依赖类型 2 */
    class MultiInjectDepMissing2

    @Component
    class MultiInjectCtorBean {
        /** 实际被选中的构造器标识 */
        val which: String

        /** 参数更多的构造器：依赖不可解析（2 参 > 1 参，排序后为首选但会失败） */
        @Inject
        constructor(a: MultiInjectDepMissing, b: MultiInjectDepMissing2) {
            which = "missing"
        }

        /** 参数较少的构造器：依赖可解析（回退目标） */
        @Inject
        constructor(dep: MultiInjectDepPresent) {
            which = dep.label()
        }
    }

    @Component
    class LazyConcreteDep

    @Component
    class LazyConcreteRequiredConsumer {
        @Inject
        @Lazy
        lateinit var dep: LazyConcreteDep
    }

    @Component
    class LazyConcreteOptionalConsumer {
        @Inject(required = false)
        @Lazy
        var dep: LazyConcreteDep? = null
    }

    // ── A-P0-04 夹具 ──

    interface ProxiedConcreteContract {
        fun work(): String
    }

    @Component
    class ProxiedConcreteServiceImpl : ProxiedConcreteContract {
        override fun work(): String = "done"
    }

    @Aspect
    class ConcreteAspect {
        @Before("execution(ProxiedConcreteServiceImpl.work)")
        fun before() {
            // 仅用于触发 AOP 代理
        }
    }

    @Component
    class LazyProxiedConcreteConsumer {
        @Inject
        @Lazy
        lateinit var service: ProxiedConcreteServiceImpl
    }

    // ── A-P1-06 夹具 ──

    interface InterfacePostEnableProduct {
        companion object {
            @Volatile
            var postEnableInvoked: Boolean = false
        }
    }

    class PostEnableProductImpl : InterfacePostEnableProduct {
        @PostEnable
        fun onEnable() {
            InterfacePostEnableProduct.postEnableInvoked = true
        }
    }

    @Configuration
    class PostEnableProductConfig {
        @Bean
        fun postEnableProduct(): InterfacePostEnableProduct = PostEnableProductImpl()
    }

    interface InterfacePreDestroyProduct {
        companion object {
            @Volatile
            var preDestroyInvoked: Boolean = false
        }
    }

    class PreDestroyProductImpl : InterfacePreDestroyProduct {
        @PreDestroy
        fun onDestroy() {
            InterfacePreDestroyProduct.preDestroyInvoked = true
        }
    }

    @Configuration
    class PreDestroyProductConfig {
        @Bean
        fun preDestroyProduct(): InterfacePreDestroyProduct = PreDestroyProductImpl()
    }

    // ── A-P1-08 夹具 ──

    @Component
    class MethodDep

    @Component
    class OptionalMethodInjectConsumer {
        var injected: Boolean = false

        @Inject(required = false)
        fun setup(dep: MissingMethodDep) {
            injected = true
        }
    }

    /** 故意不注册此类型 */
    class MissingMethodDep

    @Component
    class RequiredMethodInjectConsumer {
        var injected: Boolean = false

        @Inject
        fun setup(dep: MethodDep) {
            injected = true
        }
    }
}

package top.wcpe.taboolib.ioc.aop

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import top.wcpe.taboolib.ioc.IocTestContext
import top.wcpe.taboolib.ioc.annotation.*
import top.wcpe.taboolib.ioc.bean.AdviceType
import top.wcpe.taboolib.ioc.bean.MethodInvocation
import top.wcpe.taboolib.ioc.bean.PointcutExpression

/**
 * AOP 运行时健壮性回归测试 —— 覆盖 E5 分片的四条缺陷：
 *
 * - A-P1-12：非法切点表达式解析失败时，只能丢弃该通知并告警，绝不能冒泡导致整个容器 / 插件 enable 失败。
 * - A-P1-13：通知方法签名非法时，必须在扫描期拦截并告警，避免运行期抛异常或被静默吞掉。
 * - A-P1-14：仅命中 static 方法的切点（JDK 动态代理无法承载）必须产生告警。
 * - C-P2-04：`AspectDefinition` 零引用死类型已归档（此测试不涉及代码，仅由构建保证其不再存在）。
 */
class AopRuntimeRobustnessTest {

    // ── A-P1-12：非法切点只跳过一个通知，容器不崩、其余通知仍生效 ──

    @Test
    fun `invalid pointcut should skip only that advice and keep others working`() {
        MixedValidityAspect.callLog.clear()

        val aspect = MixedValidityAspect()
        // 不应抛异常
        val advisors = AspectScanner.scan(aspect, MixedValidityAspect::class.java)

        // 非法切点的通知被跳过：3 个通知（2 合法 + 1 非法）→ 只保留 2 个
        assertEquals(2, advisors.size, "非法切点的通知应被跳过，合法通知应保留")
        assertTrue(
            advisors.none { it.adviceMethod.name == "invalidPointcutAdvice" },
            "签名非法切点的通知不应出现在 Advisor 列表中"
        )
        assertTrue(
            advisors.any { it.adviceMethod.name == "validBefore" },
            "合法 @Before 通知应被保留"
        )
        assertTrue(
            advisors.any { it.adviceMethod.name == "validAround" },
            "合法 @Around 通知应被保留"
        )
    }

    @Test
    fun `aspect with invalid pointcut should not break container initialization`() {
        MixedValidityAspect.callLog.clear()

        val ctx = IocTestContext()
        ctx.register(MixedValidityAspect::class.java)
        ctx.register(MixedValidityTargetImpl::class.java)
        // 不应抛异常 —— 此前会在 initialize() 中冒泡导致整个容器初始化失败
        assertDoesNotThrow { ctx.initialize() }

        val service = ctx.getBean(MixedValidityTarget::class.java)
        assertNotNull(service)
        val result = service!!.run()

        // 合法通知仍生效
        assertEquals("ran", result)
        assertTrue(MixedValidityAspect.callLog.contains("validBefore:run"))
        assertTrue(MixedValidityAspect.callLog.contains("validAround:run"))
    }

    @Test
    fun `all advisors from aspect with single invalid pointcut still scanned for valid ones`() {
        // 直接验证 AspectScanner 对单条非法切点容错，逐条独立
        val advisors = AspectScanner.scan(OnlyInvalidAspect(), OnlyInvalidAspect::class.java)
        assertTrue(advisors.isEmpty(), "全部切点非法时应返回空列表且不抛异常")
    }

    // ── A-P1-13：@Around 签名错误被扫描期拦截 ──

    @Test
    fun `around advice with wrong signature should be rejected at scan time`() {
        val advisors = AspectScanner.scan(BadAroundAspect(), BadAroundAspect::class.java)

        // @Around 必须恰好 1 个 MethodInvocation 参数；此处的坏通知应被跳过
        assertTrue(
            advisors.none { it.adviceMethod.name == "badAround" },
            "@Around 参数数量/类型错误必须被扫描期拦截"
        )
        assertTrue(
            advisors.any { it.adviceMethod.name == "goodAround" },
            "合法的 @Around 应保留"
        )
    }

    @Test
    fun `wrong around signature should not throw at runtime`() {
        val ctx = IocTestContext()
        ctx.register(BadAroundAspect::class.java)
        ctx.register(MixedValidityTargetImpl::class.java)
        assertDoesNotThrow { ctx.initialize() }

        val service = ctx.getBean(MixedValidityTarget::class.java)
        // 坏 @Around 被拦截后，目标方法应正常执行
        assertEquals("ran", service!!.run())
    }

    @Test
    fun `after returning and after throwing with too many params should be rejected`() {
        val advisors = AspectScanner.scan(BadReturnThrowAspect(), BadReturnThrowAspect::class.java)

        assertTrue(
            advisors.none { it.adviceMethod.name == "badAfterReturning" },
            "@AfterReturning 参数过多应被拦截"
        )
        assertTrue(
            advisors.none { it.adviceMethod.name == "badAfterThrowing" },
            "@AfterThrowing 参数过多应被拦截"
        )
        assertTrue(
            advisors.any { it.adviceMethod.name == "goodAfterReturning" },
            "合法 @AfterReturning（0 或 1 参）应保留"
        )
    }

    @Test
    fun `all advice types with legal signatures should be kept`() {
        val advisors = AspectScanner.scan(LegalSignatureAspect(), LegalSignatureAspect::class.java)
        val byType = advisors.groupBy { it.adviceType }

        assertEquals(1, byType[AdviceType.BEFORE]?.size ?: 0)
        assertEquals(1, byType[AdviceType.AFTER]?.size ?: 0)
        assertEquals(1, byType[AdviceType.AROUND]?.size ?: 0)
        assertEquals(1, byType[AdviceType.AFTER_RETURNING]?.size ?: 0)
        assertEquals(1, byType[AdviceType.AFTER_THROWING]?.size ?: 0)
    }

    // ── A-P1-14：仅命中 static 方法的切点必须告警 ──

    @Test
    fun `static only pointcut should still be matched but warn`() {
        val registry = AdvisorRegistry()
        val advice = StaticOnlyAspect()
        val advisors = AspectScanner.scan(advice, StaticOnlyAspect::class.java)
        assertEquals(1, advisors.size, "static 切点的 Advisor 应被解析")
        registry.registerAll(advisors)

        // 应能匹配到 static 方法（保持既有行为），但内部会输出告警（无法直接断言日志，
        // 这里验证匹配发生且 Advisor 不丢失 —— 告警由 warning() 输出，见 StaticOnlyWarningSink 测试）
        val matched = registry.findMatchingAdvisors(StaticHolder::class.java)
        assertEquals(1, matched.size, "static 方法匹配的 Advisor 应保留匹配结果")
    }

    // ── 通知器与拦截链的集成补充 ──

    @Test
    fun `after returning advice should receive return value`() {
        ReturnCaptureAspect.clear()
        val ctx = IocTestContext()
        ctx.register(ReturnCaptureAspect::class.java)
        ctx.register(MixedValidityTargetImpl::class.java)
        ctx.initialize()

        val service = ctx.getBean(MixedValidityTarget::class.java)
        assertEquals("ran", service!!.run())
        assertTrue(ReturnCaptureAspect.callLog.contains("return:ran"))
    }

    @Test
    fun `after throwing advice should receive throwable`() {
        ThrowCaptureAspect.clear()
        val ctx = IocTestContext()
        ctx.register(ThrowCaptureAspect::class.java)
        ctx.register(ThrowingTargetImpl::class.java)
        ctx.initialize()

        val service = ctx.getBean(ThrowingTarget::class.java)
        assertThrows(RuntimeException::class.java) { service!!.boom() }
        assertTrue(ThrowCaptureAspect.callLog.any { it.startsWith("throw:") })
    }
}

// ───────────────────────── 测试夹具 ─────────────────────────

interface MixedValidityTarget {
    fun run(): String
}

@Component
class MixedValidityTargetImpl : MixedValidityTarget {
    override fun run(): String = "ran"
}

/**
 * 混合合法性切面：1 个非法切点 + 2 个合法通知。
 * 非法切点必须只丢弃自身，不能影响其余通知。
 */
@Aspect
class MixedValidityAspect {

    companion object {
        val callLog = mutableListOf<String>()
    }

    @Before("this-is-not-a-valid-pointcut")   // 无点号 → 解析失败
    fun invalidPointcutAdvice() {
        callLog.add("invalid:should-never-run")
    }

    @Before("execution(MixedValidityTargetImpl.run)")
    fun validBefore() {
        callLog.add("validBefore:run")
    }

    @Around("execution(MixedValidityTargetImpl.run)")
    fun validAround(invocation: MethodInvocation): Any? {
        callLog.add("validAround:run")
        return invocation.proceed()
    }
}

/** 仅有非法切点的切面 —— 应返回空列表且不抛异常。 */
@Aspect
class OnlyInvalidAspect {

    @Before("totallyInvalidExpression")
    fun bad() {
    }
}

/** @Around 签名错误（0 参 / 2 参）+ 1 个合法 @Around。 */
@Aspect
class BadAroundAspect {

    @Around("execution(MixedValidityTargetImpl.run)")
    fun badAround() {   // 缺少 MethodInvocation 参数
    }

    @Around("execution(MixedValidityTargetImpl.run)")
    fun goodAround(invocation: MethodInvocation): Any? {
        return invocation.proceed()
    }
}

/** @AfterReturning / @AfterThrowing 参数过多。 */
@Aspect
class BadReturnThrowAspect {

    @AfterReturning("execution(MixedValidityTargetImpl.run)")
    fun badAfterReturning(result: Any?, extra: Any?) {   // 应为 0 或 1 参
    }

    @AfterThrowing("execution(MixedValidityTargetImpl.run)")
    fun badAfterThrowing(t: Throwable, extra: Any?) {    // 应为 0 或 1 参
    }

    @AfterReturning("execution(MixedValidityTargetImpl.run)")
    fun goodAfterReturning(result: Any?) {
    }
}

/** 全合法签名。 */
@Aspect
class LegalSignatureAspect {

    @Before("execution(MixedValidityTargetImpl.run)")
    fun b() {
    }

    @After("execution(MixedValidityTargetImpl.run)")
    fun a() {
    }

    @Around("execution(MixedValidityTargetImpl.run)")
    fun ar(invocation: MethodInvocation): Any? = invocation.proceed()

    @AfterReturning("execution(MixedValidityTargetImpl.run)")
    fun r(result: Any?) {
    }

    @AfterThrowing("execution(MixedValidityTargetImpl.run)")
    fun t(ex: Throwable) {
    }
}

/** 仅命中 static 方法的切面。 */
object StaticHolder {
    @JvmStatic
    fun staticWork(): String = "static"
}

@Aspect
class StaticOnlyAspect {

    @Before("execution(StaticHolder.staticWork)")
    fun beforeStatic() {
    }
}

/** @AfterReturning 捕获返回值。 */
@Aspect
class ReturnCaptureAspect {

    companion object {
        val callLog = mutableListOf<String>()
        fun clear() = callLog.clear()
    }

    @AfterReturning("execution(MixedValidityTargetImpl.run)")
    fun capture(result: Any?) {
        callLog.add("return:$result")
    }
}

interface ThrowingTarget {
    fun boom(): String
}

@Component
class ThrowingTargetImpl : ThrowingTarget {
    override fun boom(): String = throw RuntimeException("kaboom")
}

/** @AfterThrowing 捕获异常。 */
@Aspect
class ThrowCaptureAspect {

    companion object {
        val callLog = mutableListOf<String>()
        fun clear() = callLog.clear()
    }

    @AfterThrowing("execution(ThrowingTargetImpl.boom)")
    fun capture(ex: Throwable) {
        callLog.add("throw:${ex.message}")
    }
}

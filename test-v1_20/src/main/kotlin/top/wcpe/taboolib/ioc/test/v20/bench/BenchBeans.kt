package top.wcpe.taboolib.ioc.test.v20.bench

import top.wcpe.taboolib.ioc.annotation.Around
import top.wcpe.taboolib.ioc.annotation.Aspect
import top.wcpe.taboolib.ioc.annotation.Service
import top.wcpe.taboolib.ioc.bean.MethodInvocation
import java.util.concurrent.atomic.AtomicInteger

/**
 * 基准测试用的 Bean 与切面。
 *
 * 这里刻意做成「**接口 + 实现类**」而不是具体类，因为 IoC 当前的 AOP 后端只有 JDK 动态代理
 * （见 `AopProxyFactory`）：只有实现了接口的 Bean 才会被真正代理。
 * 因此本组 Bean 既能跑「无切面」路径，也能跑「被切面代理」路径，
 * 从而量出 **JDK 动态代理的每次调用额外开销**。
 *
 * 对照：同模块的 [top.wcpe.taboolib.ioc.test.v20.service.GreetingService20] 是具体类，
 * 被切面命中时只能打印「AOP 代理跳过」告警（通知永不执行）。
 */
interface BenchService {
    fun echo(value: Int): Int
}

/** 不被任何切面命中，用于量「容器解析 + 普通方法调用」的基线。 */
interface PlainService {
    fun echo(value: Int): Int
}

/**
 * 实现里读一个 `@Volatile` 字段：既让"直接调用/容器调用/代理调用"三条路径做同等的真实工作，
 * 也避免 JIT 把纯函数调用整体折叠掉（把基准测成空循环）。
 */
@Service
class BenchServiceImpl : BenchService {
    @Volatile
    private var seed: Int = 1

    override fun echo(value: Int): Int = value + seed
}

@Service
class PlainServiceImpl : PlainService {
    @Volatile
    private var seed: Int = 1

    override fun echo(value: Int): Int = value + seed
}

/**
 * 命中 [BenchServiceImpl] 的环绕通知。
 *
 * 切点用**实现类**的简单名 —— 匹配发生在 `AdvisorRegistry.findMatchingAdvisors(targetClass)`，
 * 传入的是被代理的 Bean 类（实现类），不是接口。
 */
@Aspect
class BenchAspect {

    companion object {
        /** 通知实际执行次数，用于证明 JDK 代理是否真的把调用导到了通知里。 */
        val hits = AtomicInteger(0)
    }

    @Around("execution(BenchServiceImpl.echo)")
    fun aroundEcho(invocation: MethodInvocation): Any? {
        hits.incrementAndGet()
        return invocation.proceed()
    }
}

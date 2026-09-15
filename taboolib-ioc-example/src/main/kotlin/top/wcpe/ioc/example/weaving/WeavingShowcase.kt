package top.wcpe.ioc.example.weaving

import taboolib.common.LifeCycle
import taboolib.common.platform.Awake
import taboolib.common.platform.function.info
import top.wcpe.taboolib.ioc.annotation.Around
import top.wcpe.taboolib.ioc.annotation.Aspect
import top.wcpe.taboolib.ioc.annotation.Service
import top.wcpe.taboolib.ioc.bean.BeanContainer
import top.wcpe.taboolib.ioc.bean.MethodInvocation
import java.util.concurrent.atomic.AtomicInteger

/**
 * 编译期织入（②）的现场演示 + **真实服务端里的织入路径微基准**。
 *
 * - 未开启织入（默认）：容器只提供 JDK 动态代理，没有接口 → 日志出现 `[IoC] AOP 代理跳过`，命中数恒为 0；
 * - 开启织入（`taboolibIoc { weaving(true) }`）：方法体在构建期被改写，命中数 = 1 + 预热 + 计时次数，
 *   且**不创建任何代理**（`代理类` 是原始类名而不是 `$Proxy`）。
 *
 * 顺带给出「织入路径在真机上每次调用要多久」，便于与 JDK 代理路径（README 基准章节约 47 ns/op）对照。
 */
@Service
class ConcreteGreetingService {

    fun greet(name: String): String = "hello, $name"
}

@Aspect
class ConcreteWeavingAspect {

    companion object {
        val hits = AtomicInteger(0)
    }

    @Around("execution(ConcreteGreetingService.greet)")
    fun aroundGreet(invocation: MethodInvocation): Any? {
        hits.incrementAndGet()
        return invocation.proceed()
    }
}

@Suppress("unused")
object WeavingShowcaseReporter {

    private const val MEASURE_DELAY_MS = 25_000L
    private const val WARMUP = 50_000
    private const val MEASURE_DURATION_MS = 6_000L

    @Volatile
    private var sink: Int = 0

    @Awake(LifeCycle.ACTIVE)
    fun report() {
        val service = BeanContainer.getBean(ConcreteGreetingService::class.java)
        if (service == null) {
            info("[IoC-Weave] 具体类未解析到实例（容器未就绪）")
            return
        }
        val result = service.greet("weaving")
        val woven = top.wcpe.taboolib.ioc.aop.WovenTarget::class.java.isInstance(service)
        info("[IoC-Weave] 具体类切面命中=${ConcreteWeavingAspect.hits.get()} 结果=$result 代理类=${service.javaClass.name}")

        // 织入未开启时不做计时：这里测到的只是普通方法调用，报出来会被误读成「织入路径开销」。
        if (!woven) {
            info("[IoC-Weave] 织入未开启（weaving(false)）：跳过计时。开启后本行会变为织入路径的真机 ns/op。")
            return
        }

        // 计时放到独立线程 + 等服务器静下来：启用阶段 JIT 未编译、且在与世界加载抢 CPU，
        // 在那里测出来的数字没有意义（实测会高一个数量级）。
        // 采用「按时长跑」而不是固定次数：这样窗口足够长，可以用 arthas `monitor` 独立观测同一段调用。
        Thread({
            try {
                Thread.sleep(MEASURE_DELAY_MS)
                var i = 0
                while (i < WARMUP) {
                    sink += service.greet("warm").length
                    i++
                }
                val hitsBefore = ConcreteWeavingAspect.hits.get()
                var ops = 0L
                val start = System.nanoTime()
                val deadline = start + MEASURE_DURATION_MS * 1_000_000L
                while (System.nanoTime() < deadline) {
                    sink += service.greet("bench").length
                    ops++
                }
                val nanos = System.nanoTime() - start
                val hitsAfter = ConcreteWeavingAspect.hits.get()
                info(
                    "[IoC-Weave] 织入路径真机稳态: ${"%.2f".format(nanos.toDouble() / ops)} ns/op" +
                        "（${ops} 次 / ${MEASURE_DURATION_MS}ms，含 @Around 通知 + 参数装箱；预热 $WARMUP 次）"
                )
                info("[IoC-Weave] 切面命中交叉核对: 窗口内 +${hitsAfter - hitsBefore}，累计=${hitsAfter}")
            } catch (t: Throwable) {
                info("[IoC-Weave] 织入路径计时失败: ${t.message}")
            }
        }, "ioc-weave-measure").apply { isDaemon = true }.start()
    }
}

package top.wcpe.taboolib.ioc.test.v20.bench

import taboolib.common.LifeCycle
import taboolib.common.platform.Awake
import taboolib.common.platform.function.info
import taboolib.common.platform.function.warning
import java.io.File
import taboolib.common.Inject as TabooLibInject

/**
 * 基准测试的执行器：在**独立线程**上跑，绝不占用服务端主线程。
 *
 * 两种触发方式：
 * 1. 命令 `/iocbench [scale]`（人工/联调）
 * 2. 自动化：环境变量或系统属性（便于 CI / 脚本一键跑完）
 *
 * | 变量 / 属性 | 含义 | 默认 |
 * |---|---|---|
 * | `IOC_BENCH_AUTORUN` / `-Dioc.bench.autorun` | `1`/`true` 时插件启用后自动跑 | 关闭 |
 * | `IOC_BENCH_OUT` / `-Dioc.bench.out` | 结果 JSON 输出路径 | `<服务端工作目录>/ioc-benchmark-result.json` |
 * | `IOC_BENCH_SCALE` / `-Dioc.bench.scale` | 规模倍率（迭代数/压力时长按比例缩放） | `1.0` |
 * | `IOC_BENCH_DELAY_MS` / `-Dioc.bench.delay` | 启用后延迟多少毫秒再开跑（等服务器彻底静下来） | `3000` |
 * | `IOC_BENCH_SHUTDOWN` / `-Dioc.bench.shutdown` | `1`/`true` 时跑完自动关服（便于脚本收尾） | 关闭 |
 */
object BenchRunner {

    @Volatile
    private var running = false

    /** 结果默认落盘位置（服务端工作目录下）。 */
    fun defaultOutFile(): File = File(System.getProperty("user.dir"), "ioc-benchmark-result.json")

    /**
     * 异步跑完整套件。
     *
     * @param scale 规模倍率
     * @param outPath 结果文件路径，null 用默认值
     * @param shutdownAfter 跑完是否关服
     * @param delayMs 启动延迟
     */
    fun runAsync(scale: Double, outPath: String?, shutdownAfter: Boolean, delayMs: Long) {
        if (running) {
            warning("[IoC-Bench] 已有一轮基准在执行中，忽略本次请求")
            return
        }
        running = true
        val out = if (outPath.isNullOrBlank()) defaultOutFile() else File(outPath)
        Thread({
            try {
                if (delayMs > 0) Thread.sleep(delayMs)
                info("[IoC-Bench] 开始执行（scale=$scale, out=${out.absolutePath}）")
                IocBenchmark.run(scale, out)
            } catch (t: Throwable) {
                warning("[IoC-Bench] 执行失败: $t")
            } finally {
                running = false
                if (shutdownAfter) shutdownServer()
            }
        }, "ioc-benchmark").apply {
            isDaemon = true
        }.start()
    }

    /** 尽力而为地关服（复用 Bukkit 反射，避免与 paper-api 版本耦合）。 */
    private fun shutdownServer() {
        try {
            Class.forName("org.bukkit.Bukkit").getMethod("shutdown").invoke(null)
        } catch (t: Throwable) {
            warning("[IoC-Bench] 自动关服失败: ${t.message}")
        }
    }
}

/**
 * 自动化入口：只有设置了 `IOC_BENCH_AUTORUN` 才动作，不影响正常起服。
 */
@TabooLibInject
object BenchAutoRun {

    @Awake(LifeCycle.ACTIVE)
    fun onActive() {
        val flag = envOrProp("IOC_BENCH_AUTORUN", "ioc.bench.autorun") ?: return
        if (!flag.equals("true", ignoreCase = true) && flag != "1") return
        val out = envOrProp("IOC_BENCH_OUT", "ioc.bench.out")
        val scale = envOrProp("IOC_BENCH_SCALE", "ioc.bench.scale")?.toDoubleOrNull() ?: 1.0
        val delay = envOrProp("IOC_BENCH_DELAY_MS", "ioc.bench.delay")?.toLongOrNull() ?: 3000L
        val shutdown = envOrProp("IOC_BENCH_SHUTDOWN", "ioc.bench.shutdown")
            ?.let { it.equals("true", ignoreCase = true) || it == "1" } ?: false
        info("[IoC-Bench] 检测到自动运行标记，将在 ${delay}ms 后开始基准测试")
        BenchRunner.runAsync(scale, out, shutdown, delay)
    }

    private fun envOrProp(env: String, prop: String): String? =
        System.getenv(env)?.takeIf { it.isNotBlank() }
            ?: System.getProperty(prop)?.takeIf { it.isNotBlank() }
}

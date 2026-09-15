package top.wcpe.taboolib.ioc.test.v20.bench

import taboolib.common.platform.ProxyCommandSender
import taboolib.common.platform.command.CommandBody
import taboolib.common.platform.command.CommandHeader
import taboolib.common.platform.command.subCommand

/**
 * `/iocbench [scale]` —— 手动触发基准 + 压力测试。
 *
 * 测试跑在独立线程上，命令立即返回；详细结果打印到控制台，并写入
 * `IOC_BENCH_OUT`（默认 `<服务端工作目录>/ioc-benchmark-result.json`）。
 */
@CommandHeader(
    name = "iocbench",
    aliases = ["iocb"],
    permission = "taboolib.ioc.bench",
    description = "TabooLib IoC 容器基准与压力测试"
)
object IocBenchCommand {

    @CommandBody
    val main = subCommand {
        dynamic(optional = true, comment = "规模倍率（默认 1.0，越大迭代越多/压力越久）") {
            execute<ProxyCommandSender> { sender, _, argument ->
                val scale = argument.trim().toDoubleOrNull() ?: 1.0
                sender.sendMessage("§e[IoC-Bench] 已开始（scale=$scale），结果将打印到控制台并写入结果文件")
                BenchRunner.runAsync(scale, null, shutdownAfter = false, delayMs = 0)
            }
        }
    }
}

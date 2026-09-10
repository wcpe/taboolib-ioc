package top.wcpe.taboolib.ioc.test.v20

import java.io.File
import top.wcpe.mc.testkit.harness.McTestkitEnv
import top.wcpe.mc.testkit.harness.McTestkitResultWriter

/**
 * mc-testkit 场景判定桩（test-v1_20）。
 *
 * 判定真源 = 结果文件 `build/mc-testkit/results/smoke.properties` 的 `status=PASS`。
 * 无编排手工直跑（env 缺失）时回退到 stdout 约定，便于本地调试。
 *
 * 说明：本桩不含业务逻辑，只负责把「IoC 注入是否成功」的既有结论落盘。
 * 调用 Java 静态成员时用 `McTestkitEnv.envOrNull(...)` 形式（Kotlin 直接调用静态方法，
 * 不要写成 `McTestkitEnv.INSTANCE`）。
 */
object McTestkitVerdict {

    /** 无编排时的 stdout 通过标记。 */
    private const val PASS_MARKER = "[TabooLibIocTestV20] PASS"

    /** 无编排时的 stdout 失败前缀。 */
    private const val FAIL_PREFIX = "[TabooLibIocTestV20] FAIL: "

    /**
     * 上报场景结论。
     *
     * @param pass 是否通过
     * @param message 结论说明（失败时为失败原因）
     * @return true 表示处于 mc-testkit 编排下（结果文件已落盘，调用方应收尾关服）；
     *         false 表示无编排（仅打印约定，调用方不应关服，以免破坏单测/手工环境）。
     */
    fun report(pass: Boolean, message: String): Boolean {
        val resultPath = McTestkitEnv.envOrNull(McTestkitEnv.RESULT_FILE)
        if (resultPath != null) {
            val writer = McTestkitResultWriter(File(resultPath))
            if (pass) writer.pass(message) else writer.fail(message)
            return true
        }
        println(if (pass) PASS_MARKER else FAIL_PREFIX + message)
        return false
    }

    /**
     * 关服（尽力而为）：复用 Bukkit 反射，避免 main 源集 compileOnly paper-api 之外的耦合。
     *
     * 任何失败都**不抛出**——关服属收尾动作，不应让场景判定/单测因环境差异（如 MockBukkit
     * 未实现 `Bukkit.shutdown`）而失败。
     */
    fun shutdownServer() {
        try {
            Class.forName("org.bukkit.Bukkit").getMethod("shutdown").invoke(null)
        } catch (e: Throwable) {
            // 尽力而为：忽略关服失败（例如 MockBukkit 的 UnimplementedOperationException）
        }
    }
}

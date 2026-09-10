package top.wcpe.ioc.example

import java.io.File
import java.nio.charset.Charset
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * mc-testkit 场景判定桩（taboolib-ioc-example）。
 *
 * 判定真源 = 结果文件 `build/mc-testkit/results/smoke.properties` 的 `status=PASS`。
 * 无编排手工直跑（env 缺失）时回退到 stdout 约定，便于本地调试。
 *
 * 说明：本桩不含业务逻辑，只负责把「IoC 注入是否成功」的既有结论落盘。
 *
 * 注意：本模块 target 为 Java 8，且 harness-core:0.1.0 仅兼容 JVM 17+（无法在 Java 8 插件中引用），
 * 故此处自写结果文件写出逻辑（等价于 McTestkitResultWriter 的 ATOMIC_MOVE 语义），仅用 Java 8 API。
 *
 * 结果文件路径由编排经环境变量 `MC_TESTKIT_E2E_RESULT_FILE`（绝对路径）下发。
 */
object McTestkitVerdict {

    /** mc-testkit 结果文件路径的环境变量名。 */
    private const val RESULT_FILE_ENV = "MC_TESTKIT_E2E_RESULT_FILE"

    /** 无编排时的 stdout 通过标记。 */
    private const val PASS_MARKER = "[TabooLibIocExample] PASS"

    /** 无编排时的 stdout 失败前缀。 */
    private const val FAIL_PREFIX = "[TabooLibIocExample] FAIL: "

    /**
     * 上报场景结论。
     *
     * @param pass 是否通过
     * @param message 结论说明（失败时为失败原因）
     * @return true 表示处于 mc-testkit 编排下（结果文件已落盘，调用方应收尾关服）；
     *         false 表示无编排（仅打印约定，调用方不应关服，以免破坏单测/手工环境）。
     */
    fun report(pass: Boolean, message: String): Boolean {
        val resultPath = System.getenv(RESULT_FILE_ENV)?.trim()?.takeIf { it.isNotEmpty() }
        if (resultPath != null) {
            writeResultFile(File(resultPath), if (pass) "PASS" else "FAIL", message)
            return true
        }
        println(if (pass) PASS_MARKER else FAIL_PREFIX + message)
        return false
    }

    /**
     * 关服（尽力而为）：复用 Bukkit 反射，避免 main 源集对 paper-api 的编译期耦合。
     *
     * 任何失败都**不抛出**——关服属收尾动作，不应让场景判定/单测因环境差异而失败。
     */
    fun shutdownServer() {
        try {
            Class.forName("org.bukkit.Bukkit").getMethod("shutdown").invoke(null)
        } catch (e: Throwable) {
            // 尽力而为：忽略关服失败
        }
    }

    /**
     * 原子写结果文件（UTF-8 properties：status / message）。
     *
     * 先写同目录临时文件，再 ATOMIC_MOVE 替换；若不支持原子移动则退化为普通替换。
     *
     * @param target 结果文件目标路径
     * @param status `PASS` 或 `FAIL`
     * @param message 结论说明
     */
    private fun writeResultFile(target: File, status: String, message: String) {
        val parent = target.parentFile
        if (parent != null && !parent.exists()) {
            parent.mkdirs()
        }
        val content = buildString {
            append("status=").append(status).append('\n')
            append("message=").append(escape(message)).append('\n')
        }
        val tmp = File(parent, target.name + ".tmp")
        Files.write(tmp.toPath(), content.toByteArray(Charset.forName("UTF-8")))
        try {
            Files.move(
                tmp.toPath(),
                target.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE
            )
        } catch (e: Exception) {
            // 退化路径：不接受原子移动时用普通替换
            Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    /**
     * 转义 properties 值中的特殊字符（反斜杠、换行、回车），保证单行可被 Properties 解析。
     */
    private fun escape(value: String): String {
        return value
            .replace("\\", "\\\\")
            .replace("\r", "\\r")
            .replace("\n", "\\n")
    }
}

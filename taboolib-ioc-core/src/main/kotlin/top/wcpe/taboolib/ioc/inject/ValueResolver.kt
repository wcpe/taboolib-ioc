package top.wcpe.taboolib.ioc.inject

import taboolib.common.platform.function.warning
import java.io.InputStream
import java.util.Properties
import java.util.concurrent.ConcurrentHashMap

/**
 * @Value 属性表达式解析器。
 *
 * 支持格式：
 * - `${property.name}` — 从配置文件或系统属性读取
 * - `${property.name:default}` — 带默认值
 * - 纯文本 — 直接作为字面量
 *
 * 注意：仅当整串恰好是 `${...}` 时才解析；含 `${}` 的混合文本（如 `"${a} - ${b}"`）
 * **不做多段替换**，按字面量返回并给出 warning（见 [resolve] 之下的私有实现）。
 */
object ValueResolver {

    private val PLACEHOLDER_REGEX = Regex("""\$\{([^}]+)}""")
    private val loadedProperties = ConcurrentHashMap<String, String>()

    /**
     * 加载 .properties 格式的配置文件。
     *
     * @param path classpath 相对路径
     * @param classLoader 用于加载资源的 ClassLoader
     */
    fun loadProperties(path: String, classLoader: ClassLoader = Thread.currentThread().contextClassLoader) {
        val inputStream: InputStream? = classLoader.getResourceAsStream(path)
        if (inputStream == null) {
            warning("[IoC] 配置文件未找到: $path")
            return
        }
        inputStream.use { stream ->
            if (path.endsWith(".yml") || path.endsWith(".yaml")) {
                // 简单的 YAML 解析：只支持 key: value 格式的扁平属性
                stream.bufferedReader().forEachLine { line ->
                    val trimmed = line.trim()
                    if (trimmed.isNotEmpty() && !trimmed.startsWith("#")) {
                        val colonIndex = trimmed.indexOf(':')
                        if (colonIndex > 0) {
                            val key = trimmed.substring(0, colonIndex).trim()
                            val value = trimmed.substring(colonIndex + 1).trim()
                                .removeSurrounding("\"").removeSurrounding("'")
                            loadedProperties[key] = value
                        }
                    }
                }
            } else {
                val props = Properties()
                props.load(stream)
                for ((key, value) in props) {
                    loadedProperties[key.toString()] = value.toString()
                }
            }
        }
    }

    /**
     * 手动设置属性值（用于测试）。
     */
    fun setProperty(key: String, value: String) {
        loadedProperties[key] = value
    }

    /**
     * 清除所有已加载的属性。
     */
    fun clearProperties() {
        loadedProperties.clear()
    }

    /**
     * 解析属性表达式并转换为目标类型。
     */
    fun resolve(expression: String, targetType: Class<*>): Any? {
        val raw = resolveExpression(expression) ?: return null
        return convertType(raw, targetType)
    }

    /**
     * 解析属性表达式。
     *
     * 语义（A-P1-07 决策）：仅当**整串**恰好是 `${...}`（[Regex.matchEntire]）时才解析，
     * 支持 `${key}` 与 `${key:default}`。混合文本（如 `"${a} - ${b}"`）**不做多段替换**，
     * 按字面量原样返回——但会补一条 warning，消除「静默注入含 `${}` 字面量」的缺陷。
     * 多段替换属于功能扩展，不在当前范围内。
     */
    private fun resolveExpression(expression: String): String? {
        val match = PLACEHOLDER_REGEX.matchEntire(expression)
        if (match != null) {
            val inner = match.groupValues[1]
            val colonIndex = inner.indexOf(':')
            return if (colonIndex >= 0) {
                val key = inner.substring(0, colonIndex)
                val default = inner.substring(colonIndex + 1)
                getProperty(key) ?: default
            } else {
                getProperty(inner)
            }
        }
        // 混合文本：含 ${...} 但不是纯占位符 → 明确告警，避免静默注入字面量
        if (PLACEHOLDER_REGEX.containsMatchIn(expression)) {
            warning(
                "[IoC] @Value 检测到含 \${} 的混合文本表达式未被解析: '$expression'。" +
                    "当前仅支持纯占位符（\${key} 或 \${key:default}）。" +
                    "如需多段替换，请拆分为多个 @Value 字段，或改用纯占位符。"
            )
        }
        // 纯文本字面量
        return expression
    }

    /**
     * 查询属性值（已加载的配置文件 > 系统属性）。
     *
     * 公开给条件模块（如 `@ConditionalOnProperty`）复用，使条件判断与 `@Value`
     * 读取**同一属性源**、行为一致。实现与 [resolveExpression] 内部取值完全一致。
     *
     * @param key 属性键
     * @return 命中则返回属性值，否则返回 null
     */
    fun getProperty(key: String): String? {
        return loadedProperties[key] ?: System.getProperty(key)
    }

    private fun convertType(value: String, targetType: Class<*>): Any? {
        return try {
            when (targetType) {
                String::class.java -> value
                Int::class.java, java.lang.Integer::class.java -> value.toInt()
                Long::class.java, java.lang.Long::class.java -> value.toLong()
                Double::class.java, java.lang.Double::class.java -> value.toDouble()
                Float::class.java, java.lang.Float::class.java -> value.toFloat()
                Boolean::class.java, java.lang.Boolean::class.java -> convertBoolean(value)
                Short::class.java, java.lang.Short::class.java -> value.toShort()
                Byte::class.java, java.lang.Byte::class.java -> value.toByte()
                else -> {
                    // C-P2-08：char/Character 由 K20 白名单判定为不支持，此处保持现状不新增支持，
                    // 但在告警信息中显式点名，避免用户误以为只是「漏写」了 char。
                    val charHint = if (targetType == Char::class.java || targetType == java.lang.Character::class.java) {
                        "（char/Character 不受支持，请改用 String 或 int 承载字符）"
                    } else {
                        ""
                    }
                    warning(
                        "[IoC] @Value 不支持的目标类型: ${targetType.name}，仅支持基本类型和 String" + charHint
                    )
                    null
                }
            }
        } catch (e: NumberFormatException) {
            warning("[IoC] @Value 类型转换失败: '$value' -> ${targetType.simpleName}")
            null
        }
    }

    /**
     * 将字符串转换为 Boolean。
     *
     * 仅接受 `"true"`（忽略大小写）为 true，其余一律为 false。
     * 对非规范字面量（既非 `true` 也非 `false`）补一条 warning，
     * 避免拼写错误（如 `"ture"`、`"yes"`）被静默当作 false。
     */
    private fun convertBoolean(value: String): Boolean {
        if (value.equals("true", ignoreCase = true)) return true
        if (value.equals("false", ignoreCase = true)) return false
        warning(
            "[IoC] @Value Boolean 转换: '$value' 不是规范的布尔字面量（true/false），按 false 处理。" +
                "请检查配置拼写。"
        )
        return false
    }
}

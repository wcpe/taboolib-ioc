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
        // 纯文本字面量
        return expression
    }

    /**
     * 从已加载的配置文件和系统属性中查找属性值。
     * 优先级：已加载的配置文件 > 系统属性
     */
    private fun getProperty(key: String): String? {
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
                Boolean::class.java, java.lang.Boolean::class.java -> value.toBoolean()
                Short::class.java, java.lang.Short::class.java -> value.toShort()
                Byte::class.java, java.lang.Byte::class.java -> value.toByte()
                else -> {
                    warning("[IoC] @Value 不支持的目标类型: ${targetType.name}，仅支持基本类型和 String")
                    null
                }
            }
        } catch (e: NumberFormatException) {
            warning("[IoC] @Value 类型转换失败: '$value' -> ${targetType.simpleName}")
            null
        }
    }
}

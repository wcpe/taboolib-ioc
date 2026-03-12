package top.wcpe.taboolib.ioc.inject

import taboolib.common.platform.function.warning

/**
 * @Value 属性表达式解析器。
 *
 * 支持格式：
 * - `${property.name}` — 从系统属性读取
 * - `${property.name:default}` — 带默认值
 * - 纯文本 — 直接作为字面量
 */
object ValueResolver {

    private val PLACEHOLDER_REGEX = Regex("""\$\{([^}]+)}""")

    /**
     * 解析属性表达式并转换为目标类型。
     *
     * @param expression 属性表达式
     * @param targetType 目标字段类型
     * @return 解析后的值，无法解析时返回 null
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
                System.getProperty(key, default)
            } else {
                System.getProperty(inner)
            }
        }
        // 纯文本字面量
        return expression
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

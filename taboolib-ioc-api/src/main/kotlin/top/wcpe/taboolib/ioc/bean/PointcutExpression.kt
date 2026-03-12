package top.wcpe.taboolib.ioc.bean

import java.lang.reflect.Method

/**
 * 切点表达式解析与匹配。
 *
 * 支持简化的 execution 表达式格式：
 * - `execution(com.example.MyService.doSomething)` — 精确匹配
 * - `execution(*.doSomething)` — 匹配所有类的 doSomething 方法
 * - `execution(com.example..*.doSomething)` — 匹配包下所有类的 doSomething 方法
 * - `execution(com.example.MyService.*)` — 匹配 MyService 的所有方法
 */
class PointcutExpression private constructor(
    private val classPattern: String,
    private val methodPattern: String
) {

    /**
     * 判断给定的类和方法是否匹配此切点表达式。
     */
    fun matches(targetClass: Class<*>, method: Method): Boolean {
        return matchesClass(targetClass) && matchesMethod(method)
    }

    private fun matchesClass(targetClass: Class<*>): Boolean {
        if (classPattern == "*") return true

        val className = targetClass.name
        // 包通配符：com.example..* 匹配 com.example 及其子包下的所有类
        if (classPattern.endsWith("..*")) {
            val packagePrefix = classPattern.dropLast(3)
            return className.startsWith(packagePrefix)
        }
        // 精确匹配（支持简单类名和全限定名）
        return className == classPattern || targetClass.simpleName == classPattern
    }

    private fun matchesMethod(method: Method): Boolean {
        if (methodPattern == "*") return true
        return method.name == methodPattern
    }

    override fun toString(): String = "execution($classPattern.$methodPattern)"

    companion object {

        /**
         * 解析切点表达式字符串。
         *
         * @param expression 格式为 `execution(类模式.方法模式)` 或直接 `类模式.方法模式`
         */
        fun parse(expression: String): PointcutExpression {
            var expr = expression.trim()

            // 去掉 execution(...) 包裹
            if (expr.startsWith("execution(") && expr.endsWith(")")) {
                expr = expr.substring(10, expr.length - 1).trim()
            }

            // 找最后一个 . 作为类和方法的分隔
            val lastDot = expr.lastIndexOf('.')
            require(lastDot > 0) { "无效的切点表达式: $expression" }

            val classPattern = expr.substring(0, lastDot)
            val methodPattern = expr.substring(lastDot + 1)

            return PointcutExpression(classPattern, methodPattern)
        }
    }
}

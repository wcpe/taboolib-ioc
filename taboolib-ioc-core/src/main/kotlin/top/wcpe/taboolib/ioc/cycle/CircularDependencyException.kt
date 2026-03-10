package top.wcpe.taboolib.ioc.cycle

/**
 * 循环依赖异常
 */
class CircularDependencyException(
    val beanName: String,
    val dependencyChain: List<String>
) : Exception(buildMessage(beanName, dependencyChain)) {

    companion object {
        fun buildMessage(beanName: String, chain: List<String>): String {
            return """
                |检测到循环依赖: $beanName
                |依赖链: ${chain.joinToString(" -> ")}
                |
                |建议:
                |1. 使用字段注入替代构造函数注入
                |2. 将其中一个依赖拆到方法或字段注入阶段
                |3. 重构代码，消除循环依赖
            """.trimMargin()
        }
    }
}

package top.wcpe.taboolib.ioc.bean

object BeanScopes {

    const val SINGLETON = "singleton"
    const val PROTOTYPE = "prototype"
    const val THREAD = "thread"
    const val REFRESH = "refresh"

    fun normalize(scope: String?): String {
        return scope
            ?.trim()
            ?.lowercase()
            ?.takeIf { it.isNotEmpty() }
            ?: SINGLETON
    }

    fun isStandard(scope: String): Boolean {
        val normalized = normalize(scope)
        return normalized == SINGLETON || normalized == PROTOTYPE
    }

    /**
     * 判断是否为内置作用域（标准 + 框架提供的扩展作用域）。
     */
    fun isBuiltin(scope: String): Boolean {
        val normalized = normalize(scope)
        return normalized == SINGLETON || normalized == PROTOTYPE ||
            normalized == THREAD || normalized == REFRESH
    }
}

package top.wcpe.taboolib.ioc.bean

object BeanScopes {

    const val SINGLETON = "singleton"
    const val PROTOTYPE = "prototype"

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
}

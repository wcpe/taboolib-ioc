package top.wcpe.taboolib.ioc.condition

import top.wcpe.taboolib.ioc.annotation.ConditionalOnProperty

/**
 * [ConditionalOnProperty] 条件实现。
 *
 * 检查系统属性是否匹配指定值。
 */
object OnPropertyCondition {

    fun matches(annotation: ConditionalOnProperty): Boolean {
        val propertyValue = System.getProperty(annotation.name)

        if (propertyValue == null) {
            return annotation.matchIfMissing
        }

        // havingValue 为空字符串时，仅检查属性是否存在
        if (annotation.havingValue.isEmpty()) {
            return true
        }

        return propertyValue == annotation.havingValue
    }
}

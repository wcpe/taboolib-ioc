package top.wcpe.taboolib.ioc.condition

import top.wcpe.taboolib.ioc.annotation.ConditionalOnProperty
import top.wcpe.taboolib.ioc.inject.ValueResolver

/**
 * [ConditionalOnProperty] 条件实现。
 *
 * C-P2-19：属性来源必须与 `@Value` **一致**，即同时可见 `@PropertySource` 加载的配置与系统属性。
 * 原先只读 `System.getProperty`，导致 `@PropertySource` 中的配置对条件不可见、
 * 与同源的 `@Value` 结论相反。这里复用 [ValueResolver.getProperty] 的统一来源
 * （优先级：已加载的配置文件 > 系统属性）。
 */
object OnPropertyCondition {

    fun matches(annotation: ConditionalOnProperty): Boolean {
        val propertyValue = ValueResolver.getProperty(annotation.name)

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

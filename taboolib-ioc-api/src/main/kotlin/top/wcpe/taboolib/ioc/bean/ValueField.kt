package top.wcpe.taboolib.ioc.bean

import java.lang.reflect.Field

/**
 * @Value 字段描述。
 *
 * @property field 字段对象
 * @property expression 属性表达式（如 ${property.name:default}）
 */
class ValueField(
    val field: Field,
    val expression: String
) {
    init {
        field.isAccessible = true
    }
}

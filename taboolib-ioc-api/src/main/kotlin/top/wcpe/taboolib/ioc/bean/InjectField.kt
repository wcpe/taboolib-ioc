package top.wcpe.taboolib.ioc.bean

import java.lang.reflect.Field

/**
 * 注入字段描述。
 *
 * 存储需要依赖注入的字段信息。
 *
 * @property field 字段对象
 * @property requiredType 需要注入的类型
 * @property nameQualifier 名称限定符（来自 @Named 或 @Resource）
 * @property lazy 是否延迟注入
 */
class InjectField(
    val field: Field,
    val requiredType: Class<*>,
    val nameQualifier: String?,
    val lazy: Boolean = false
) {
    init {
        field.isAccessible = true
    }
}

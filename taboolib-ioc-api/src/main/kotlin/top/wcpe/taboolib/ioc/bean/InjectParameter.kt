package top.wcpe.taboolib.ioc.bean

/**
 * 注入参数描述。
 *
 * 存储方法参数的依赖注入信息。
 *
 * @property type 参数类型
 * @property nameQualifier 名称限定符（来自 @Named）
 * @property lazy 是否延迟注入
 */
class InjectParameter(
    val type: Class<*>,
    val nameQualifier: String?,
    val lazy: Boolean = false
)

package top.wcpe.taboolib.ioc.bean

/**
 * 切面元数据定义。
 *
 * @property name 切面 Bean 名称
 * @property type 切面类型
 * @property advisors 此切面包含的所有通知器
 */
class AspectDefinition(
    val name: String,
    val type: Class<*>,
    val advisors: List<Advisor>
)

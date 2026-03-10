package top.wcpe.taboolib.ioc.bean

import java.lang.reflect.Method

/**
 * 注入方法描述。
 *
 * 存储需要依赖注入的方法（setter）信息。
 *
 * @property method 方法对象
 * @property parameters 方法参数列表
 */
class InjectMethod(
    val method: Method,
    val parameters: List<InjectParameter>
) {
    init {
        method.isAccessible = true
    }
}

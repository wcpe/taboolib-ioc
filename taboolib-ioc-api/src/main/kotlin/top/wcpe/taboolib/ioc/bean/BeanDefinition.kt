package top.wcpe.taboolib.ioc.bean

import java.lang.reflect.Constructor
import java.lang.reflect.Method

/**
 * Bean 元数据定义。
 *
 * 存储 Bean 的所有元信息，包括名称、类型、构造函数、注入点等。
 *
 * @property name Bean 名称
 * @property type Bean 类型
 * @property constructor 用于创建实例的构造函数
 * @property injectFields 需要注入的字段列表
 * @property injectMethods 需要注入的方法列表
 * @property postConstruct 初始化后回调方法
 * @property preDestroy 销毁前回调方法
 */
class BeanDefinition(
    val name: String,
    val type: Class<*>,
    val constructor: Constructor<*>,
    val injectFields: List<InjectField>,
    val injectMethods: List<InjectMethod>,
    val postConstruct: Method?,
    val preDestroy: Method?
) {
    init {
        constructor.isAccessible = true
        postConstruct?.isAccessible = true
        preDestroy?.isAccessible = true
    }
}

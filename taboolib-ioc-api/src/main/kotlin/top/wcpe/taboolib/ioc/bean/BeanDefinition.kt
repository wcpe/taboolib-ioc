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
 * @property constructor 用于创建实例的构造函数（@Bean 工厂方法产物为 null）
 * @property injectFields 需要注入的字段列表
 * @property injectMethods 需要注入的方法列表
 * @property postConstruct 初始化后回调方法
 * @property postEnable 插件 ENABLE 后统一执行的回调方法
 * @property preDestroy 销毁前回调方法
 * @property lazyInit 是否延迟初始化
 * @property scope Bean 作用域
 * @property isPrimary 是否为首选 Bean
 * @property order 排序优先级，值越小优先级越高
 * @property valueFields 需要属性值注入的字段列表
 * @property factoryBeanName 工厂 Bean 名称（@Bean 方法所在的 @Configuration 类）
 * @property factoryMethod 工厂方法（@Bean 标注的方法）
 */
class BeanDefinition(
    val name: String,
    val type: Class<*>,
    val constructor: Constructor<*>?,
    val injectFields: List<InjectField>,
    val injectMethods: List<InjectMethod>,
    val postConstruct: Method?,
    val postEnable: Method?,
    val preDestroy: Method?,
    val constructorParameters: List<InjectParameter>,
    val dependencies: List<InjectParameter>,
    val lazyInit: Boolean = false,
    val scope: String = BeanScopes.SINGLETON,
    val isAspect: Boolean = false,
    val isPrimary: Boolean = false,
    val order: Int = Int.MAX_VALUE,
    val valueFields: List<ValueField> = emptyList(),
    val factoryBeanName: String? = null,
    val factoryMethod: Method? = null
) {
    init {
        constructor?.isAccessible = true
        postConstruct?.isAccessible = true
        postEnable?.isAccessible = true
        preDestroy?.isAccessible = true
        factoryMethod?.isAccessible = true
    }

    /** 是否为工厂方法产物（@Bean） */
    fun isFactoryBean(): Boolean = factoryBeanName != null && factoryMethod != null

    fun isSingletonScope(): Boolean = BeanScopes.normalize(scope) == BeanScopes.SINGLETON

    fun isPrototypeScope(): Boolean = BeanScopes.normalize(scope) == BeanScopes.PROTOTYPE

    fun isEagerSingleton(): Boolean = isSingletonScope() && !lazyInit
}

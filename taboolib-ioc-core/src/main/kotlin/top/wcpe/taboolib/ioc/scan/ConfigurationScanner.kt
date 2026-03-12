package top.wcpe.taboolib.ioc.scan

import top.wcpe.taboolib.ioc.annotation.Bean
import top.wcpe.taboolib.ioc.annotation.Configuration
import top.wcpe.taboolib.ioc.annotation.Lazy
import top.wcpe.taboolib.ioc.annotation.Order
import top.wcpe.taboolib.ioc.annotation.Primary
import top.wcpe.taboolib.ioc.annotation.Scope
import top.wcpe.taboolib.ioc.bean.BeanDefinition
import top.wcpe.taboolib.ioc.bean.BeanScopes
import top.wcpe.taboolib.ioc.bean.InjectParameter

/**
 * 配置类扫描器 — 解析 @Configuration 类中的 @Bean 方法，生成 BeanDefinition 列表。
 */
object ConfigurationScanner {

    /**
     * 扫描配置类中的 @Bean 方法。
     *
     * @param configClass 配置类
     * @param configBeanName 配置类自身的 Bean 名称
     * @return @Bean 方法对应的 BeanDefinition 列表
     */
    fun scan(configClass: Class<*>, configBeanName: String): List<BeanDefinition> {
        if (!configClass.isAnnotationPresent(Configuration::class.java)) {
            return emptyList()
        }

        return configClass.declaredMethods
            .filter { it.isAnnotationPresent(Bean::class.java) }
            .map { method ->
                val beanAnnotation = method.getAnnotation(Bean::class.java)
                val beanName = beanAnnotation.value.ifEmpty { method.name }
                val returnType = method.returnType

                require(returnType != Void.TYPE) {
                    "@Bean 方法 ${configClass.name}.${method.name}() 返回类型不能为 void"
                }

                // 解析工厂方法参数作为依赖
                val parameters = method.parameters.map { param ->
                    InjectParameter(
                        type = param.type,
                        nameQualifier = null
                    )
                }

                val isPrimary = method.isAnnotationPresent(Primary::class.java)
                val order = method.getAnnotation(Order::class.java)?.value ?: Int.MAX_VALUE
                val lazyInit = method.getAnnotation(Lazy::class.java)?.value == true
                val scope = method.getAnnotation(Scope::class.java)?.value?.let { BeanScopes.normalize(it) }
                    ?: BeanScopes.SINGLETON

                BeanDefinition(
                    name = beanName,
                    type = returnType,
                    constructor = null,
                    injectFields = emptyList(),
                    injectMethods = emptyList(),
                    postConstruct = null,
                    postEnable = null,
                    preDestroy = null,
                    constructorParameters = parameters,
                    dependencies = parameters,
                    lazyInit = lazyInit,
                    scope = scope,
                    isPrimary = isPrimary,
                    order = order,
                    factoryBeanName = configBeanName,
                    factoryMethod = method
                )
            }
    }
}

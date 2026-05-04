package top.wcpe.taboolib.ioc.inject

/**
 * Bean 实例化异常
 *
 * 当 Bean 实例化过程中发生错误时抛出，例如构造函数参数无法解析。
 */
class BeanInstantiationException(
    val beanName: String,
    message: String,
    cause: Throwable? = null
) : Exception(message, cause) {

    companion object {
        /**
         * 构建构造函数参数缺失的异常消息
         */
        fun missingConstructorParameter(
            beanName: String,
            beanType: Class<*>,
            parameterIndex: Int,
            parameterType: Class<*>,
            nameQualifier: String?
        ): BeanInstantiationException {
            val qualifierInfo = if (nameQualifier != null) {
                " (名称限定符: '$nameQualifier')"
            } else {
                ""
            }

            val message = """
                |无法实例化 Bean '$beanName' (类型: ${beanType.name})
                |构造函数参数 #$parameterIndex 无法解析:
                |  - 参数类型: ${parameterType.name}$qualifierInfo
                |  - 原因: 找不到匹配的 Bean
                |
                |建议:
                |1. 确保依赖的 Bean 已注册 (检查 @Component/@Service/@Repository 注解)
                |2. 如果使用了 @Named 限定符，确保目标 Bean 的名称匹配
                |3. 检查依赖 Bean 的作用域和初始化顺序
                |4. 考虑使用 @Lazy 注解延迟加载该依赖
            """.trimMargin()

            return BeanInstantiationException(beanName, message)
        }

        /**
         * 构建工厂方法参数缺失的异常消息
         */
        fun missingFactoryMethodParameter(
            beanName: String,
            factoryBeanName: String,
            factoryMethodName: String,
            parameterIndex: Int,
            parameterType: Class<*>,
            nameQualifier: String?
        ): BeanInstantiationException {
            val qualifierInfo = if (nameQualifier != null) {
                " (名称限定符: '$nameQualifier')"
            } else {
                ""
            }

            val message = """
                |无法通过 @Bean 工厂方法创建 Bean '$beanName'
                |工厂方法: $factoryBeanName.$factoryMethodName()
                |方法参数 #$parameterIndex 无法解析:
                |  - 参数类型: ${parameterType.name}$qualifierInfo
                |  - 原因: 找不到匹配的 Bean
                |
                |建议:
                |1. 确保依赖的 Bean 已注册
                |2. 如果使用了 @Named 限定符，确保目标 Bean 的名称匹配
                |3. 检查依赖 Bean 的初始化顺序 (考虑使用 @DependsOn)
                |4. 考虑使用 @Lazy 注解延迟加载该依赖
            """.trimMargin()

            return BeanInstantiationException(beanName, message)
        }
    }
}

package top.wcpe.taboolib.ioc.annotation

/**
 * 条件评估上下文。
 *
 * 提供条件评估所需的容器信息访问能力。
 */
interface ConditionContext {

    /**
     * 获取类加载器
     */
    fun getClassLoader(): ClassLoader

    /**
     * 检查是否包含指定名称的 Bean 定义
     */
    fun containsBeanDefinition(name: String): Boolean

    /**
     * 获取指定类型的所有 Bean 定义名称
     */
    fun getBeanNamesForType(type: Class<*>): List<String>
}

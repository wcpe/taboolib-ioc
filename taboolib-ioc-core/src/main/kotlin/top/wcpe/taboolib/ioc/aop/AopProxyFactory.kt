package top.wcpe.taboolib.ioc.aop

import java.lang.reflect.Proxy

/**
 * AOP 代理工厂。
 *
 * 判断目标 Bean 是否需要代理，如果有匹配的 Advisor 且目标实现了接口，
 * 则创建 JDK 动态代理包装。
 */
class AopProxyFactory(
    private val advisorRegistry: AdvisorRegistry
) {

    /**
     * 如果目标 Bean 有匹配的 Advisor，则包装为代理对象返回；否则返回原始实例。
     *
     * @param instance 原始 Bean 实例
     * @param beanClass Bean 的原始类型
     * @return 代理对象或原始实例
     */
    fun wrapIfNecessary(instance: Any, beanClass: Class<*>): Any {
        val matchingAdvisors = advisorRegistry.findMatchingAdvisors(beanClass)
        if (matchingAdvisors.isEmpty()) {
            return instance
        }

        // 收集目标类实现的所有接口
        val interfaces = collectInterfaces(beanClass)
        if (interfaces.isEmpty()) {
            // 无接口，无法创建 JDK 动态代理
            return instance
        }

        val handler = JdkDynamicAopProxy(instance, beanClass, matchingAdvisors)
        return Proxy.newProxyInstance(
            beanClass.classLoader ?: Thread.currentThread().contextClassLoader,
            interfaces,
            handler
        )
    }

    private fun collectInterfaces(clazz: Class<*>): Array<Class<*>> {
        val interfaces = linkedSetOf<Class<*>>()
        var current: Class<*>? = clazz
        while (current != null && current != Any::class.java) {
            interfaces.addAll(current.interfaces)
            current = current.superclass
        }
        return interfaces.toTypedArray()
    }
}

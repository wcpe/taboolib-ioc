package top.wcpe.taboolib.ioc.bean

/**
 * Bean 后处理器扩展点。
 *
 * 允许在 Bean 初始化前后对实例进行自定义处理。
 * 实现此接口的 Bean 会被自动发现并注册。
 */
interface BeanPostProcessor {
    /**
     * 在 Bean 初始化回调（@PostConstruct）之前调用。
     *
     * @param bean Bean 实例
     * @param beanName Bean 名称
     * @return 处理后的 Bean 实例（可以是原始实例或包装后的代理）
     */
    fun postProcessBeforeInitialization(bean: Any, beanName: String): Any = bean

    /**
     * 在 Bean 初始化回调（@PostConstruct）之后、AOP 代理之前调用。
     *
     * @param bean Bean 实例
     * @param beanName Bean 名称
     * @return 处理后的 Bean 实例（可以是原始实例或包装后的代理）
     */
    fun postProcessAfterInitialization(bean: Any, beanName: String): Any = bean
}

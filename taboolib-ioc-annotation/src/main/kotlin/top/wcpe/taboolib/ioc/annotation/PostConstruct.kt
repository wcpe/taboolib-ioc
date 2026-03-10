package top.wcpe.taboolib.ioc.annotation

/**
 * Bean 初始化后回调
 * 在依赖注入完成后调用，方法必须无参数
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class PostConstruct

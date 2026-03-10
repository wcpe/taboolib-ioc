package top.wcpe.taboolib.ioc.annotation

/**
 * Bean 销毁前回调
 * 在容器关闭时调用，方法必须无参数
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class PreDestroy

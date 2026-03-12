package top.wcpe.taboolib.ioc.annotation

/**
 * 插件 ENABLE 后统一执行的回调
 * 在所有 Bean 创建完毕、object 注入完成后调用，方法必须无参数
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class PostEnable

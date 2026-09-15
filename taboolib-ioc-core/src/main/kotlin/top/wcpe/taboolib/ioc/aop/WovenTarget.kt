package top.wcpe.taboolib.ioc.aop

/**
 * 编译期织入标记接口。
 *
 * 由 `top.wcpe.taboolib.ioc` Gradle 插件在构建期**自动加到被织入的类**上（字节码层面），
 * 运行期的 [AopProxyFactory] 见到它就不再创建动态代理 —— 因为这些类的方法体里已经
 * 直接调用了 [AopWeavingRuntime]，再代理会导致通知执行两次。
 *
 * 用「给类加接口」而不是「写索引文件」当标记，是为了**天然 relocate 安全**：
 * 类自身的字节码会被 TabooLib 的 relocate 一并重写，而资源文件里的类名字符串不会。
 *
 * 使用者不需要关心这个接口，也不要自己实现它。
 */
interface WovenTarget

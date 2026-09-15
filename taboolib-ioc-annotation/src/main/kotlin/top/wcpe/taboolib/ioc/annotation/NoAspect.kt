package top.wcpe.taboolib.ioc.annotation

/**
 * 声明式排除 AOP 切面。
 *
 * - 标注在**类**上：该 Bean 完全不被代理（连 JDK 动态代理都不创建），零额外开销。
 * - 标注在**方法**上：该方法不参与切点匹配 —— 切点即使命中也不会触发通知。
 *   可以标在实现类的方法上（容器会同时检查接口方法与实现方法）。
 *
 * ## 什么时候用
 *
 * AOP 每次调用都有固定开销（JDK 动态代理 + 通知链，实测约几十纳秒；即便优化后仍高于直接调用）。
 * 对于**每 tick / 高频循环**里调用的方法，正确做法是把它从切面里摘出去：
 *
 * ```kotlin
 * @Service
 * class TickService {
 *     @NoAspect                        // 每 tick 都调，不进切面
 *     fun onTick() { ... }
 *
 *     fun doRareThing() { ... }        // 被切点命中的方法照常生效
 * }
 * ```
 *
 * 更彻底的做法是把高频逻辑拆到独立的类并整类标注 `@NoAspect`。
 */
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
annotation class NoAspect

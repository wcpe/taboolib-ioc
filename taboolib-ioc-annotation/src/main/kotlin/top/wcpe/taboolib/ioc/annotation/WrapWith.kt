package top.wcpe.taboolib.ioc.annotation

import kotlin.reflect.KClass

/**
 * 用**手写装饰器**包装 Bean（零运行期开销的组合方式）。
 *
 * 与 AOP 代理的区别：
 * - AOP：容器生成代理/织入代码，每次调用有固定开销，但可以按切点批量横切；
 * - `@WrapWith`：你自己写一个装饰器类（持有被包装对象并手工转发），容器负责在
 *   `@PostConstruct` 之后、AOP 代理之前把实例换成装饰器 —— 调用路径上**没有任何额外开销**。
 *
 * ```kotlin
 * // 1) 手写装饰器：实现与目标相同的接口，构造器接收被包装对象
 * class LoggingUserService(private val delegate: UserService) : UserService {
 *     override fun find(id: String): User? {
 *         println("find($id)")
 *         return delegate.find(id)
 *     }
 * }
 *
 * // 2) 在原实现上声明
 * @Service
 * @WrapWith(LoggingUserService::class)
 * class UserServiceImpl : UserService { ... }
 *
 * // 3) 取出来的就是装饰器
 * val svc = BeanContainer.getBean(UserService::class.java)  // -> LoggingUserService
 * ```
 *
 * 构造器匹配规则：优先选择「单参数且参数类型能接收被包装实例」的构造器
 * （即参数为原实现类或其任一接口）；找不到时回退到任意单参数构造器；仍失败则告警并保留原实例。
 *
 * 装饰器本身仍可被 AOP 代理（如果它的类被切点命中）—— 两者可以叠加使用。
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
annotation class WrapWith(val value: KClass<*>)

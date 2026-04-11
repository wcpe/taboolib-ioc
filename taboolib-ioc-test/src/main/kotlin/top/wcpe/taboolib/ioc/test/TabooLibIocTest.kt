package top.wcpe.taboolib.ioc.test

import kotlin.reflect.KClass
import org.junit.jupiter.api.extension.ExtendWith
import taboolib.common.LifeCycle

/**
 * 类似 SpringBootTest 的测试注解。
 *
 * 标注后会自动引导 TabooLib 生命周期和 IoC 容器。
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
@ExtendWith(TabooLibIocTestExtension::class)
annotation class TabooLibIocTest(
	vararg val components: KClass<*>,
	val targetLifeCycle: LifeCycle = LifeCycle.ACTIVE,
	val invokePostEnable: Boolean = true,
	val observable: Boolean = false,
	val enablePrimitiveBootstrap: Boolean = false
)
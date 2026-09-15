package top.wcpe.taboolib.ioc.aop

import taboolib.common.platform.function.warning
import top.wcpe.taboolib.ioc.annotation.WrapWith
import top.wcpe.taboolib.ioc.bean.BeanPostProcessor

/**
 * 内置后处理器：实现 [WrapWith] —— 用手写装饰器替换 Bean 实例。
 *
 * 时点与 AOP 的关系（见 `LifecycleManager.initializeInstance`）：
 * `注入 → BeanPostProcessor(before) → @PostConstruct → BeanPostProcessor(after) → AOP 包装`，
 * 因此装饰器会在 AOP 之前生效 —— 装饰器类若被切点命中，仍会被代理包裹（可叠加）。
 *
 * 与 AOP 的取舍：装饰器是**编译期确定的手写转发**，调用路径上零额外开销；
 * 代价是每个需要横切的方法都得自己写一遍转发（适合少量、高频的调用点）。
 */
internal object WrapWithPostProcessor : BeanPostProcessor {

    override fun postProcessAfterInitialization(bean: Any, beanName: String): Any {
        val annotation = bean.javaClass.getAnnotation(WrapWith::class.java) ?: return bean
        val decoratorClass = annotation.value.java
        val constructor = decoratorClass.constructors
            .filter { it.parameterCount == 1 }
            .let { candidates ->
                candidates.firstOrNull { it.parameterTypes[0].isInstance(bean) } ?: candidates.firstOrNull()
            }
        if (constructor == null) {
            warning(
                "[IoC] @WrapWith 已跳过：${decoratorClass.name} 没有可用的单参数构造器" +
                    "（Bean=${beanName}, 类型=${bean.javaClass.name}）"
            )
            return bean
        }
        return try {
            constructor.isAccessible = true
            constructor.newInstance(bean)
        } catch (t: Throwable) {
            warning(
                "[IoC] @WrapWith 包装失败，保留原实例：Bean=$beanName, " +
                    "装饰器=${decoratorClass.name}, 原因=${t.cause?.message ?: t.message}"
            )
            bean
        }
    }
}

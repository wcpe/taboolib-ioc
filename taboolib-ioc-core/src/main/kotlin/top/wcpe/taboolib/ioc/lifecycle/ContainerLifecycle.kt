package top.wcpe.taboolib.ioc.lifecycle

import taboolib.common.LifeCycle
import taboolib.common.platform.Awake
import taboolib.common.platform.function.debug
import taboolib.common.platform.function.registerLifeCycleTask
import top.wcpe.taboolib.ioc.bean.BeanContainer
import taboolib.common.Inject as TabooLibInject

/**
 * 容器生命周期管理，集成 Taboolib 生命周期。
 *
 * 时序：
 * - LOAD 阶段：ComponentVisitor 完成组件扫描
 * - ENABLE 前置（优先级 -100）：初始化容器，创建 eager singleton
 * - ENABLE 前置（优先级 -90）：ObjectInjector 注入 object 字段
 * - ENABLE 前置（优先级 -80）：执行 @PostEnable 回调
 * - DISABLE 收尾（优先级 100）：关闭容器，调用 @PreDestroy
 *
 * 这样依赖插件在 @Awake(LifeCycle.ENABLE) 或 onEnable() 中即可使用 IoC Bean。
 */
@TabooLibInject
object ContainerLifecycle {

    @Awake(LifeCycle.LOAD)
    fun registerLifecycleTasks() {
        registerLifeCycleTask(LifeCycle.ENABLE, -100, Runnable {
            if (!BeanContainer.initialized) {
                val start = System.nanoTime()
                debug("[IoC] ENABLE 前置任务，开始初始化容器")
                BeanContainer.initialize()
                val ms = (System.nanoTime() - start) / 1_000_000.0
                debug("[IoC] 容器初始化完成，总耗时 ${"%.2f".format(ms)}ms")
            }
        })
        registerLifeCycleTask(LifeCycle.ENABLE, -80, Runnable {
            if (BeanContainer.initialized) {
                val start = System.nanoTime()
                debug("[IoC] ENABLE 前置任务，执行 @PostEnable 回调")
                BeanContainer.invokePostEnable()
                val ms = (System.nanoTime() - start) / 1_000_000.0
                debug("[IoC] @PostEnable 回调完成，总耗时 ${"%.2f".format(ms)}ms")
            }
        })
        registerLifeCycleTask(LifeCycle.DISABLE, 100, Runnable {
            if (BeanContainer.initialized) {
                val start = System.nanoTime()
                debug("[IoC] DISABLE 收尾任务，关闭容器")
                BeanContainer.shutdown()
                val ms = (System.nanoTime() - start) / 1_000_000.0
                debug("[IoC] 容器关闭完成，总耗时 ${"%.2f".format(ms)}ms")
            }
        })
    }
}

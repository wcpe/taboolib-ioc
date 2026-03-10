package top.wcpe.taboolib.ioc.lifecycle

import taboolib.common.Inject as TabooLibInject
import taboolib.common.LifeCycle
import taboolib.common.platform.Awake
import taboolib.common.platform.function.debug
import taboolib.common.platform.function.registerLifeCycleTask
import top.wcpe.taboolib.ioc.bean.BeanContainer

/**
 * 容器生命周期管理，集成 Taboolib 生命周期
 */
@TabooLibInject
object ContainerLifecycle {

    @Awake(LifeCycle.ENABLE)
    fun registerLifecycleTasks() {
        registerLifeCycleTask(LifeCycle.ACTIVE, -100, Runnable {
            if (!BeanContainer.initialized) {
                debug("[IoC] ACTIVE 前置任务，开始初始化容器")
                BeanContainer.initialize()
            }
        })
        registerLifeCycleTask(LifeCycle.DISABLE, 100, Runnable {
            if (BeanContainer.initialized) {
                debug("[IoC] DISABLE 收尾任务，关闭容器")
                BeanContainer.shutdown()
            }
        })
    }
}

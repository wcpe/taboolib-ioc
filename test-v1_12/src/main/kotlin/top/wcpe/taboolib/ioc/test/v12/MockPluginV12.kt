package top.wcpe.taboolib.ioc.test.v12

import taboolib.common.Inject as TabooLibInject
import taboolib.common.LifeCycle
import taboolib.common.platform.Awake
import taboolib.common.platform.function.info
import top.wcpe.taboolib.ioc.annotation.Inject

@TabooLibInject
object MockPluginV12 {

    @Inject
    lateinit var showcase: ShowcaseV12

    @Awake(LifeCycle.ACTIVE)
    fun onActive() {
        info("Taboolib IoC TestV12 Plugin 启动")
        showcase.runAll().forEach(::info)
    }

    @Awake(LifeCycle.DISABLE)
    fun onDisable() {
        info("Taboolib IoC TestV12 Plugin 关闭")
    }
}

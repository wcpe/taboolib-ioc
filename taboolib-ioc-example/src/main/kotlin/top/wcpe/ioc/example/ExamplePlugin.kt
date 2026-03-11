package top.wcpe.ioc.example

import taboolib.common.Inject as TabooLibInject
import taboolib.common.LifeCycle
import taboolib.common.platform.Awake
import taboolib.common.platform.function.info
import top.wcpe.taboolib.ioc.bean.BeanContainer
import top.wcpe.taboolib.ioc.annotation.Inject
import top.wcpe.ioc.example.controller.ExampleFeatureController
import top.wcpe.ioc.example.model.ExampleManualToken

@TabooLibInject
object ExamplePlugin {

    @Inject
    lateinit var featureController: ExampleFeatureController

    @Awake(LifeCycle.ACTIVE)
    fun onActive() {
        BeanContainer.registerBean("exampleManualToken", ExampleManualToken("manual-ready"))
        info("Taboolib IoC Example Plugin 启动")
        featureController.runAllChecks().forEach(::info)
    }

    @Awake(LifeCycle.DISABLE)
    fun onDisable() {
        info("Taboolib IoC Example Plugin 关闭")
    }
}

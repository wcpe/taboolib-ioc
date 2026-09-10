package top.wcpe.ioc.example

import taboolib.common.LifeCycle
import taboolib.common.platform.Awake
import taboolib.common.platform.function.info
import top.wcpe.ioc.example.controller.ExampleFeatureController
import top.wcpe.ioc.example.model.ExampleManualToken
import top.wcpe.taboolib.ioc.annotation.Inject
import top.wcpe.taboolib.ioc.bean.BeanContainer
import taboolib.common.Inject as TabooLibInject

@TabooLibInject
object ExamplePlugin {

    @Inject
    lateinit var featureController: ExampleFeatureController

    @Awake(LifeCycle.ACTIVE)
    fun onActive() {
        try {
            BeanContainer.registerBean("exampleManualToken", ExampleManualToken("manual-ready"))
            info("Taboolib IoC Example Plugin 启动")
            val lines = featureController.runAllChecks()
            lines.forEach(::info)
            if (McTestkitVerdict.report(true, "ioc injection ok: ${lines.size} checks")) {
                McTestkitVerdict.shutdownServer()
            }
        } catch (e: Throwable) {
            info("[TabooLibIocExample] 判定失败: ${e.message}")
            if (McTestkitVerdict.report(false, e.message ?: e.javaClass.name)) {
                McTestkitVerdict.shutdownServer()
            }
        }
    }

    @Awake(LifeCycle.DISABLE)
    fun onDisable() {
        info("Taboolib IoC Example Plugin 关闭")
    }
}

package top.wcpe.taboolib.ioc.test.v20

import taboolib.common.LifeCycle
import taboolib.common.platform.Awake
import taboolib.common.platform.function.info
import top.wcpe.taboolib.ioc.annotation.Inject
import taboolib.common.Inject as TabooLibInject

@TabooLibInject
object MockPluginV20 {

    @Inject
    lateinit var showcase: Showcase20

    @Awake(LifeCycle.ACTIVE)
    fun onActive() {
        info("[TabooLibIocTestV20] 启动")
        try {
            val lines = showcase.runAll()
            lines.forEach(::info)
            if (McTestkitVerdict.report(true, "ioc injection ok: ${lines.size} checks")) {
                McTestkitVerdict.shutdownServer()
            }
        } catch (e: Throwable) {
            info("[TabooLibIocTestV20] 判定失败: ${e.message}")
            if (McTestkitVerdict.report(false, e.message ?: e.javaClass.name)) {
                McTestkitVerdict.shutdownServer()
            }
        }
    }

    @Awake(LifeCycle.DISABLE)
    fun onDisable() {
        info("[TabooLibIocTestV20] 关闭")
    }
}

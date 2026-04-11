package top.wcpe.taboolib.ioc.test

import taboolib.common.LifeCycle
import taboolib.common.PrimitiveIO
import taboolib.common.TabooLib
import taboolib.common.classloader.IsolatedClassLoader
import taboolib.common.platform.Plugin
import top.wcpe.taboolib.ioc.IocTestContext

/**
 * 测试专用上下文：在 IocTestContext 基础上引导 TabooLib 生命周期。
 */
class TabooLibIocTestContext {

    companion object {
        @Volatile
        private var primitiveBootstrapped = false

        @Synchronized
        private fun ensurePrimitiveBootstrap() {
            if (primitiveBootstrapped) {
                return
            }
            // 对齐 BukkitPlugin 静态块：先初始化 IsolatedClassLoader，再进入生命周期。
            IsolatedClassLoader.init(TabooLibIocTestContext::class.java)
            primitiveBootstrapped = true
        }
    }

    private val delegate = IocTestContext()
    private var observableEnabled = false

    private fun pluginInstance(): Plugin? = Plugin.getInstance()

    fun bootstrap(
        targetLifeCycle: LifeCycle,
        invokePostEnable: Boolean,
        observable: Boolean = false,
        enablePrimitiveBootstrap: Boolean = false,
        vararg componentClasses: Class<*>
    ) {
        observableEnabled = observable
        log(observable, "bootstrap start, target=$targetLifeCycle")
        if (enablePrimitiveBootstrap) {
            ensurePrimitiveBootstrap()
            log(observable, "primitive bootstrap ready")
        } else {
            log(observable, "primitive bootstrap disabled, use gradle classpath")
        }
        TabooLib.setStopped(false)

        componentClasses.forEach { delegate.registerWithCondition(it) }
        log(observable, "registered ${componentClasses.size} components")

        advanceTo(targetLifeCycle, invokePostEnable, observable)
        log(observable, "bootstrap complete, current=${TabooLib.getCurrentLifeCycle()}")
    }

    private fun advanceTo(targetLifeCycle: LifeCycle, invokePostEnable: Boolean, observable: Boolean) {
        if (targetLifeCycle.ordinal >= LifeCycle.CONST.ordinal) {
            log(observable, "lifeCycle -> CONST")
            TabooLib.lifeCycle(LifeCycle.CONST)
        }

        if (targetLifeCycle.ordinal >= LifeCycle.INIT.ordinal) {
            log(observable, "lifeCycle -> INIT")
            TabooLib.lifeCycle(LifeCycle.INIT)
        }

        if (targetLifeCycle.ordinal >= LifeCycle.LOAD.ordinal) {
            log(observable, "lifeCycle -> LOAD")
            TabooLib.lifeCycle(LifeCycle.LOAD)
            pluginInstance()?.onLoad()
        }

        if (targetLifeCycle.ordinal >= LifeCycle.ENABLE.ordinal) {
            // IoC 在 ENABLE 前完成初始化，模拟 Bukkit onEnable 里可访问容器。
            log(observable, "initialize ioc context")
            delegate.initialize()
            log(observable, "lifeCycle -> ENABLE")
            TabooLib.lifeCycle(LifeCycle.ENABLE)
            pluginInstance()?.onEnable()
            if (invokePostEnable) {
                log(observable, "invoke @PostEnable callbacks")
                delegate.invokePostEnable()
            }
        }

        if (targetLifeCycle.ordinal >= LifeCycle.ACTIVE.ordinal) {
            log(observable, "lifeCycle -> ACTIVE")
            TabooLib.lifeCycle(LifeCycle.ACTIVE)
            pluginInstance()?.onActive()
        }
    }

    fun shutdown() {
        log(observableEnabled, "shutdown start")
        delegate.lifecycleManager.shutdown()
        if (!TabooLib.isStopped()) {
            pluginInstance()?.onDisable()
        }
        log(observableEnabled, "lifeCycle -> DISABLE")
        TabooLib.lifeCycle(LifeCycle.DISABLE)
        log(observableEnabled, "shutdown complete")
    }

    fun <T> getBean(type: Class<T>, name: String? = null): T? {
        return delegate.getBean(type, name)
    }

    private fun log(observable: Boolean, message: String) {
        if (observable) {
            PrimitiveIO.println("[TabooLibIocTest] $message")
        }
    }
}
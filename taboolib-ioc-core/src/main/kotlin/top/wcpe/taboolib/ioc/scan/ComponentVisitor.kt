package top.wcpe.taboolib.ioc.scan

import taboolib.common.LifeCycle
import taboolib.common.inject.ClassVisitor
import taboolib.common.io.runningClassMapInJar
import taboolib.common.platform.Awake
import taboolib.common.platform.function.debug
import top.wcpe.taboolib.ioc.bean.BeanContainer
import taboolib.common.Inject as TabooLibInject

/**
 * 在 ENABLE 阶段主动扫描插件 Jar 内的组件类。
 *
 * 这里不依赖 TabooLib 的逐类 ClassVisitor 回调，
 * 避免项目包名包含 ".taboolib." 时被误判为 TabooLib 内部类而被跳过。
 */
@TabooLibInject
@Awake
object ComponentVisitor : ClassVisitor(1) {

    override fun getLifeCycle(): LifeCycle = LifeCycle.ENABLE

    @Awake(LifeCycle.ENABLE)
    fun scanAll() {
        val allClasses = runningClassMapInJar.values
            .mapNotNull { it.toClass() }
            .distinct()
        val scanPackages = ComponentScanPackages.resolve(allClasses)
        val candidateClasses = allClasses.filter { clazz ->
            ComponentScanPackages.matches(clazz, scanPackages)
        }

        if (scanPackages.isNotEmpty()) {
            debug("[IoC] 启用 @ComponentScan，扫描包: ${scanPackages.joinToString()}")
        }

        var scanned = 0
        for (javaClass in candidateClasses) {
            val definition = BeanContainer.getScanner().scan(javaClass) ?: continue
            if (BeanContainer.getRegistry().contains(definition.name)) continue

            BeanContainer.getRegistry().register(definition)
            scanned++
            debug("[IoC] 扫描到组件: ${definition.name} (${definition.type.simpleName})")
        }
        debug("[IoC] 组件扫描完成，共注册 $scanned 个 Bean")
    }
}

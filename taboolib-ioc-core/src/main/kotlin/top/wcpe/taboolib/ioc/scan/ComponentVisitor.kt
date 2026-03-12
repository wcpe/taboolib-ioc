package top.wcpe.taboolib.ioc.scan

import taboolib.common.LifeCycle
import taboolib.common.inject.ClassVisitor
import taboolib.common.io.runningClassMapInJar
import taboolib.common.platform.Awake
import taboolib.common.platform.function.debug
import top.wcpe.taboolib.ioc.bean.BeanContainer
import taboolib.common.Inject as TabooLibInject

/**
 * 在 LOAD 阶段主动扫描插件 Jar 内的组件类。
 *
 * 这里不依赖 TabooLib 的逐类 ClassVisitor 回调，
 * 避免项目包名包含 ".taboolib." 时被误判为 TabooLib 内部类而被跳过。
 *
 * 扫描在 LOAD 阶段完成，使容器可以在 ENABLE 阶段完成初始化，
 * 让依赖插件在 ENABLE 阶段即可使用 IoC 注入的 Bean。
 */
@TabooLibInject
@Awake
object ComponentVisitor : ClassVisitor(1) {

    override fun getLifeCycle(): LifeCycle = LifeCycle.LOAD

    @Awake(LifeCycle.LOAD)
    fun scanAll() {
        val totalStart = System.nanoTime()

        val classLoadStart = System.nanoTime()
        val allClasses = runningClassMapInJar.values
            .mapNotNull { it.toClass() }
            .distinct()
        val classLoadMs = (System.nanoTime() - classLoadStart) / 1_000_000.0
        debug("[IoC] 类加载完成，共 ${allClasses.size} 个类，耗时 ${"%.2f".format(classLoadMs)}ms")

        val resolveStart = System.nanoTime()
        val scanPackages = ComponentScanPackages.resolve(allClasses)
        val resolveMs = (System.nanoTime() - resolveStart) / 1_000_000.0
        debug("[IoC] @ComponentScan 包解析耗时 ${"%.2f".format(resolveMs)}ms")

        val filterStart = System.nanoTime()
        val candidateClasses = allClasses.filter { clazz ->
            ComponentScanPackages.matches(clazz, scanPackages)
        }
        val filterMs = (System.nanoTime() - filterStart) / 1_000_000.0
        debug("[IoC] 包过滤完成，候选 ${candidateClasses.size}/${allClasses.size} 个类，耗时 ${"%.2f".format(filterMs)}ms")

        if (scanPackages.isNotEmpty()) {
            debug("[IoC] 启用 @ComponentScan，扫描包: ${scanPackages.joinToString()}")
        }

        var scanned = 0
        var scanTotalNs = 0L
        var registerTotalNs = 0L
        for (javaClass in candidateClasses) {
            val scanStart = System.nanoTime()
            val definition = BeanContainer.getScanner().scan(javaClass)
            scanTotalNs += System.nanoTime() - scanStart
            if (definition == null) continue
            if (BeanContainer.getRegistry().contains(definition.name)) continue

            val regStart = System.nanoTime()
            BeanContainer.getRegistry().register(definition)
            registerTotalNs += System.nanoTime() - regStart
            scanned++
            debug("[IoC] 扫描到组件: ${definition.name} (${definition.type.simpleName})")
        }
        val scanTotalMs = scanTotalNs / 1_000_000.0
        val registerTotalMs = registerTotalNs / 1_000_000.0
        val totalMs = (System.nanoTime() - totalStart) / 1_000_000.0
        debug("[IoC] 组件扫描完成，共注册 $scanned 个 Bean，元数据扫描耗时 ${"%.2f".format(scanTotalMs)}ms，注册耗时 ${"%.2f".format(registerTotalMs)}ms，总耗时 ${"%.2f".format(totalMs)}ms")
    }
}

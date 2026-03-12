package top.wcpe.taboolib.ioc.scan

import taboolib.common.LifeCycle
import taboolib.common.inject.ClassVisitor
import taboolib.common.io.runningClassMapInJar
import taboolib.common.platform.Awake
import taboolib.common.platform.function.debug
import top.wcpe.taboolib.ioc.annotation.Configuration
import top.wcpe.taboolib.ioc.bean.BeanContainer
import top.wcpe.taboolib.ioc.bean.BeanDefinition
import top.wcpe.taboolib.ioc.condition.ConditionEvaluator
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

        val conditionContext = BeanContainer.createConditionContext()

        // ── 阶段一：注册不带 @ConditionalOnBean/@ConditionalOnMissingBean 的 Bean ──
        var scanned = 0
        var skippedByCondition = 0
        var scanTotalNs = 0L
        var registerTotalNs = 0L
        val deferredBeanConditions = mutableListOf<Pair<Class<*>, BeanDefinition>>()

        for (javaClass in candidateClasses) {
            val scanStart = System.nanoTime()
            val definition = BeanContainer.getScanner().scan(javaClass)
            scanTotalNs += System.nanoTime() - scanStart
            if (definition == null) continue
            if (BeanContainer.getRegistry().contains(definition.name)) continue

            // 阶段一条件评估（@Conditional、@ConditionalOnClass、@ConditionalOnMissingClass、@ConditionalOnProperty）
            if (ConditionEvaluator.shouldSkipOnScan(javaClass, conditionContext)) {
                skippedByCondition++
                debug("[IoC] 条件不满足，跳过组件: ${definition.name} (${javaClass.simpleName})")
                continue
            }

            // 带 Bean 条件的延迟到阶段二
            if (ConditionEvaluator.hasBeanCondition(javaClass)) {
                deferredBeanConditions.add(javaClass to definition)
                continue
            }

            val regStart = System.nanoTime()
            BeanContainer.getRegistry().register(definition)
            registerTotalNs += System.nanoTime() - regStart
            scanned++
            debug("[IoC] 扫描到组件: ${definition.name} (${definition.type.simpleName})")

            // 如果是 @Configuration 类，扫描其 @Bean 方法
            if (javaClass.isAnnotationPresent(Configuration::class.java)) {
                val beanDefinitions = ConfigurationScanner.scan(javaClass, definition.name)
                for (beanDef in beanDefinitions) {
                    if (BeanContainer.getRegistry().contains(beanDef.name)) continue
                    // 评估 @Bean 方法上的条件注解
                    val beanMethod = beanDef.factoryMethod ?: continue
                    if (ConditionEvaluator.shouldSkipOnScan(beanMethod, conditionContext)) {
                        debug("[IoC] @Bean 方法条件不满足，跳过: ${beanDef.name}")
                        continue
                    }
                    if (ConditionEvaluator.hasBeanCondition(beanMethod)) {
                        deferredBeanConditions.add(javaClass to beanDef)
                        continue
                    }
                    BeanContainer.getRegistry().register(beanDef)
                    scanned++
                    debug("[IoC] 扫描到 @Bean: ${beanDef.name} (${beanDef.type.simpleName}) <- ${definition.name}")
                }
            }
        }

        // ── 阶段二：评估 @ConditionalOnBean / @ConditionalOnMissingBean ──
        if (deferredBeanConditions.isNotEmpty()) {
            debug("[IoC] 开始阶段二条件评估，共 ${deferredBeanConditions.size} 个待评估组件")
            for ((javaClass, definition) in deferredBeanConditions) {
                if (BeanContainer.getRegistry().contains(definition.name)) continue

                if (ConditionEvaluator.shouldSkipOnBeanCondition(javaClass, conditionContext)) {
                    skippedByCondition++
                    debug("[IoC] Bean 条件不满足，跳过组件: ${definition.name} (${javaClass.simpleName})")
                    continue
                }

                val regStart = System.nanoTime()
                BeanContainer.getRegistry().register(definition)
                registerTotalNs += System.nanoTime() - regStart
                scanned++
                debug("[IoC] 扫描到组件（条件装配）: ${definition.name} (${definition.type.simpleName})")

                // 如果是 @Configuration 类，扫描其 @Bean 方法
                if (javaClass.isAnnotationPresent(Configuration::class.java)) {
                    val beanDefinitions = ConfigurationScanner.scan(javaClass, definition.name)
                    for (beanDef in beanDefinitions) {
                        if (BeanContainer.getRegistry().contains(beanDef.name)) continue
                        // 评估 @Bean 方法上的条件注解
                        val beanMethod = beanDef.factoryMethod
                        if (beanMethod != null && ConditionEvaluator.shouldSkipOnScan(beanMethod, conditionContext)) {
                            debug("[IoC] @Bean 方法条件不满足，跳过（条件装配）: ${beanDef.name}")
                            continue
                        }
                        if (beanMethod != null && ConditionEvaluator.shouldSkipOnBeanCondition(beanMethod, conditionContext)) {
                            debug("[IoC] @Bean 方法 Bean 条件不满足，跳过（条件装配）: ${beanDef.name}")
                            continue
                        }
                        BeanContainer.getRegistry().register(beanDef)
                        scanned++
                        debug("[IoC] 扫描到 @Bean（条件装配）: ${beanDef.name} (${beanDef.type.simpleName}) <- ${definition.name}")
                    }
                }
            }
        }

        val scanTotalMs = scanTotalNs / 1_000_000.0
        val registerTotalMs = registerTotalNs / 1_000_000.0
        val totalMs = (System.nanoTime() - totalStart) / 1_000_000.0
        debug("[IoC] 组件扫描完成，共注册 $scanned 个 Bean，跳过 $skippedByCondition 个（条件不满足），元数据扫描耗时 ${"%.2f".format(scanTotalMs)}ms，注册耗时 ${"%.2f".format(registerTotalMs)}ms，总耗时 ${"%.2f".format(totalMs)}ms")
    }
}

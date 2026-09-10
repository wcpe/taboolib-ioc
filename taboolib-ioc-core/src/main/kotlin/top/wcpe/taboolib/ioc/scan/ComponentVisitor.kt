package top.wcpe.taboolib.ioc.scan

import taboolib.common.LifeCycle
import taboolib.common.inject.ClassVisitor
import taboolib.common.platform.Awake
import taboolib.common.platform.function.debug
import taboolib.common.platform.function.warning
import top.wcpe.taboolib.ioc.annotation.ConditionContext
import top.wcpe.taboolib.ioc.annotation.Configuration
import top.wcpe.taboolib.ioc.annotation.PropertySource
import top.wcpe.taboolib.ioc.bean.BeanContainer
import top.wcpe.taboolib.ioc.bean.BeanDefinition
import top.wcpe.taboolib.ioc.condition.ConditionEvaluator
import top.wcpe.taboolib.ioc.inject.ValueResolver
import taboolib.common.Inject as TabooLibInject

/**
 * 通过反射获取 runningClassMapInJar 中的所有 Java Class。
 *
 * 不直接引用 ReflexClass / runningClassMapInJar 属性，
 * 因为 TabooLib 的 relocate 会把 org.tabooproject.reflex 重定向到插件包名下，
 * 而且不同版本的 getter 返回类型签名不同（HashMap vs Map）。
 * 全部通过反射调用，避免编译时绑定。
 *
 * ## relocate 兼容性（relocation-proof）
 *
 * **禁止在此硬编码 `"taboolib.common.io.ProjectScannerKt"`**：
 * 宿主项目 relocate TabooLib 时该字符串不会跟着重写，`Class.forName` 必然失败。
 * 正确做法是用编译期已绑定的锚点类 [LifeCycle] 推导运行时包名：
 * `LifeCycle::class.java.name` 在 relocate 后自动变成新名字
 * （如 `com.example.ioc.common.LifeCycle`），剥掉 `.LifeCycle` 后缀即得
 * `taboolib.common` 的运行时形态，再拼出 `ProjectScannerKt` 的真实名字。
 *
 * 同时所有失败路径均为**软降级**（告警 + 返回空列表），绝不在 LOAD 阶段抛异常
 * 崩掉插件 enable；代价是 IoC 注入不可用，但错误信息可读、可定位。
 *
 * ## 确定性（A-P1-11）
 *
 * `runningClassMapInJar` 底层是 `ConcurrentHashMap`，`values` 迭代顺序不确定，
 * 会导致组件扫描 / 注册顺序跨构建、跨环境不可复现。这里按类的全限定名排序，
 * 保证每次扫描都以同一顺序处理候选类。
 */
@Suppress("UNCHECKED_CAST")
internal fun getRunningClassesInJar(): List<Class<*>> {
    // 锚点：LifeCycle 是编译期已引用的 TabooLib 类，其运行时全限定名反映实际 relocate 结果
    val scannerBase = LifeCycle::class.java.name.removeSuffix(".LifeCycle") // e.g. "taboolib.common"
    val scannerName = "$scannerBase.io.ProjectScannerKt"

    val scannerClass = try {
        Class.forName(scannerName)
    } catch (e: ClassNotFoundException) {
        warning("[IoC] 未找到 TabooLib 类扫描器: $scannerName（TabooLib 版本可能不兼容或未引入），组件扫描已跳过，IoC 注入将不可用")
        return emptyList()
    }

    val method = try {
        scannerClass.methods.first { it.name == "getRunningClassMapInJar" }
    } catch (e: NoSuchElementException) {
        warning("[IoC] TabooLib 版本不兼容: 未找到 getRunningClassMapInJar()，组件扫描已跳过，IoC 注入将不可用")
        return emptyList()
    }

    val map = try {
        method.invoke(null) as? Map<String, Any>
    } catch (e: Throwable) {
        warning("[IoC] getRunningClassMapInJar() 调用失败: ${e.message}，组件扫描已跳过，IoC 注入将不可用")
        return emptyList()
    }
    if (map == null || map.isEmpty()) {
        // C-P2-17：与其它 4 个失败分支保持一致，不再静默返回空列表。
        warning("[IoC] runningClassMapInJar 为空（TabooLib 未扫描到任何类），组件扫描已跳过，IoC 注入将不可用")
        return emptyList()
    }

    val toClass = try {
        map.values.first().javaClass.getMethod("toClass")
    } catch (e: NoSuchMethodException) {
        warning("[IoC] TabooLib 版本不兼容: ReflexClass#toClass 不存在，组件扫描已跳过，IoC 注入将不可用")
        return emptyList()
    }

    val result = ArrayList<Class<*>>(map.size)
    var failed = 0
    for (reflexClass in map.values) {
        try {
            (toClass.invoke(reflexClass) as? Class<*>)?.let { result += it }
        } catch (e: Throwable) {
            // 单个类失败不影响整体（可能是平台缺失依赖）
            failed++
            debug("[IoC] 跳过无法加载的类: ${e.message}")
        }
    }
    if (failed > 0) {
        debug("[IoC] 共 $failed 个类无法加载，已跳过")
    }
    // A-P1-11：按全限定名排序，消除 ConcurrentHashMap.values 的非确定顺序；
    // 同时按类名去重（同一 Class 可能来自不同 reflex 条目），替代语义上冗余的 distinct()。
    return orderClassesDeterministically(result)
}

/**
 * A-P1-11：将类集合规范化为**确定顺序**并按 FQCN 去重。
 *
 * `runningClassMapInJar` 底层为 `ConcurrentHashMap`，其 `values` 顺序不确定，
 * 直接迭代会导致扫描/注册顺序跨构建、跨环境不可复现。本函数：
 * 1. 按类名 `distinctBy` 去重（同一类只保留一个，跨 reflex 条目稳定）；
 * 2. 按全限定名 `sortedBy` 排序，保证任何输入顺序都产出同一输出顺序。
 *
 * 抽为纯函数以便单元测试（无需 TabooLib 运行时）。
 */
internal fun orderClassesDeterministically(classes: Iterable<Class<*>>): List<Class<*>> {
    return classes.distinctBy { it.name }.sortedBy { it.name }
}

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

        // A-P1-11：getRunningClassesInJar 已按 FQCN 排序并去重，此处无需再 distinct()
        val allClasses = getRunningClassesInJar()
        debug("[IoC] 类加载完成，共 ${allClasses.size} 个类，耗时 ${"%.2f".format((System.nanoTime() - totalStart) / 1_000_000.0)}ms")

        val scanPackages = ComponentScanPackages.resolve(allClasses)
        val candidateClasses = allClasses.filter { clazz ->
            ComponentScanPackages.matches(clazz, scanPackages)
        }
        debug("[IoC] 包过滤完成，候选 ${candidateClasses.size}/${allClasses.size} 个类")

        if (scanPackages.isNotEmpty()) {
            debug("[IoC] 启用 @ComponentScan，扫描包: ${scanPackages.joinToString()}")
        }

        val conditionContext = BeanContainer.createConditionContext()

        var scanned = 0
        var skippedByCondition = 0
        var scanTotalNs = 0L
        var registerTotalNs = 0L
        val deferredBeanConditions = mutableListOf<Pair<Class<*>, BeanDefinition>>()

        // ── 阶段一：注册不带 @ConditionalOnBean/@ConditionalOnMissingBean 的 Bean ──
        for (javaClass in candidateClasses) {
            val scanStart = System.nanoTime()
            // A-P0-05：ConfigurationScanner.scan 内部对返回 void 的 @Bean 方法软降级（告警 + 跳过），
            // 不再抛异常；因此这里无需 try/catch，scan 不会从 LOAD 阶段冒泡异常。
            val definition = BeanContainer.getScanner().scan(javaClass)
            scanTotalNs += System.nanoTime() - scanStart
            if (definition == null) continue
            if (BeanContainer.getRegistry().contains(definition.name)) {
                // C-P2-16：重名跳过改为告警，避免静默导致结果不可复现 / 难排查。
                warnDuplicate(definition.name, definition.type, "组件")
                continue
            }

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
                val stats = scanConfigurationBeans(javaClass, definition.name, conditionContext, deferredBeanConditions)
                scanned += stats.registered
                registerTotalNs += stats.registerNs
            }
        }

        // ── 阶段二：评估 @ConditionalOnBean / @ConditionalOnMissingBean ──
        if (deferredBeanConditions.isNotEmpty()) {
            debug("[IoC] 开始阶段二条件评估，共 ${deferredBeanConditions.size} 个待评估组件")
            for ((javaClass, definition) in deferredBeanConditions) {
                if (BeanContainer.getRegistry().contains(definition.name)) {
                    warnDuplicate(definition.name, definition.type, "组件（条件装配）")
                    continue
                }

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

                // 如果是 @Configuration 类，扫描其 @Bean 方法（阶段二：只评估 Bean 条件，不再延迟）
                if (javaClass.isAnnotationPresent(Configuration::class.java)) {
                    val stats = scanConfigurationBeans(
                        javaClass, definition.name, conditionContext, deferredBeanConditions, stageTwo = true
                    )
                    scanned += stats.registered
                    registerTotalNs += stats.registerNs
                }
            }
        }

        val scanTotalMs = scanTotalNs / 1_000_000.0
        val registerTotalMs = registerTotalNs / 1_000_000.0
        val totalMs = (System.nanoTime() - totalStart) / 1_000_000.0
        debug("[IoC] 组件扫描完成，共注册 $scanned 个 Bean，跳过 $skippedByCondition 个（条件不满足），元数据扫描耗时 ${"%.2f".format(scanTotalMs)}ms，注册耗时 ${"%.2f".format(registerTotalMs)}ms，总耗时 ${"%.2f".format(totalMs)}ms")
    }

    /**
     * 注册 @Configuration 类中的 @Bean 方法。
     *
     * A-P0-05：`ConfigurationScanner.scan` 已对返回 void 的 @Bean 方法软降级（告警 + 跳过），
     * 本方法不捕获其异常（scan 保证不抛）。
     *
     * A-P1-09：@PropertySource 属性加载集中在本方法入口处理。同一 `@PropertySource` 在
     * 阶段一/阶段二最多只会被处理一次（阶段一未走该配置类时由阶段二补齐），
     * 消除重复 `loadProperties` 带来的重复 warning 噪音。
     *
     * @param stageTwo true 表示当前处于阶段二：此时对已到达阶段二的元素只评估 Bean 条件，
     *   **不可再向 [deferredBeanConditions] 追加**（否则会与正在迭代的列表并发修改 / 重复处理）。
     * @return 本方法新增注册的 Bean 数与注册耗时纳秒数
     */
    private fun scanConfigurationBeans(
        javaClass: Class<*>,
        configBeanName: String,
        conditionContext: ConditionContext,
        deferredBeanConditions: MutableList<Pair<Class<*>, BeanDefinition>>,
        stageTwo: Boolean = false
    ): RegisterStats {
        // 加载 @PropertySource 配置文件（ValueResolver 内部按 path 幂等；loadedPropertySourcePaths 再去重一次）
        val loadedPaths = loadedPropertySourcePaths
        val propertySource = javaClass.getAnnotation(PropertySource::class.java)
        if (propertySource != null) {
            for (path in propertySource.value) {
                if (loadedPaths.add(path)) {
                    ValueResolver.loadProperties(path)
                    debug("[IoC] 加载配置文件: $path")
                }
            }
        }

        var registered = 0
        var registerNs = 0L
        val beanDefinitions = ConfigurationScanner.scan(javaClass, configBeanName)
        for (beanDef in beanDefinitions) {
            if (BeanContainer.getRegistry().contains(beanDef.name)) {
                warnDuplicate(beanDef.name, beanDef.type, "@Bean")
                continue
            }
            // 评估 @Bean 方法上的条件注解
            val beanMethod = beanDef.factoryMethod ?: continue
            if (ConditionEvaluator.shouldSkipOnScan(beanMethod, conditionContext)) {
                debug("[IoC] @Bean 方法条件不满足，跳过: ${beanDef.name}")
                continue
            }
            // 阶段二：直接评估 Bean 条件（不再延迟，避免并发修改 deferred 列表 / 重复处理）
            if (stageTwo) {
                if (ConditionEvaluator.shouldSkipOnBeanCondition(beanMethod, conditionContext)) {
                    debug("[IoC] @Bean 方法 Bean 条件不满足，跳过（条件装配）: ${beanDef.name}")
                    continue
                }
                val regStart = System.nanoTime()
                BeanContainer.getRegistry().register(beanDef)
                registerNs += System.nanoTime() - regStart
                registered++
                debug("[IoC] 扫描到 @Bean（条件装配）: ${beanDef.name} (${beanDef.type.simpleName}) <- $configBeanName")
                continue
            }
            // 阶段一：带 Bean 条件的 @Bean 方法延迟到阶段二
            if (ConditionEvaluator.hasBeanCondition(beanMethod)) {
                deferredBeanConditions.add(javaClass to beanDef)
                continue
            }
            val regStart = System.nanoTime()
            BeanContainer.getRegistry().register(beanDef)
            registerNs += System.nanoTime() - regStart
            registered++
            debug("[IoC] 扫描到 @Bean: ${beanDef.name} (${beanDef.type.simpleName}) <- $configBeanName")
        }
        return RegisterStats(registered, registerNs)
    }

    /**
     * A-P1-09：记录已加载的 @PropertySource 路径，避免对同一路径重复 loadProperties + debug 噪音。
     * 每次 scanAll 调用（LOAD 阶段仅一次）重置。
     */
    private val loadedPropertySourcePaths = linkedSetOf<String>()

    /**
     * A-P1-11 / C-P2-16：重名跳过时输出 warning，指明被跳过的 name 与先到者类型。
     */
    private fun warnDuplicate(name: String, type: Class<*>, kind: String) {
        warning("[IoC] 检测到同名 $kind '$name'（类型 ${type.name}），已跳过该 Bean；先前已注册的同名 Bean 优先")
    }

    /** 单次配置类扫描的统计结果。 */
    private class RegisterStats(val registered: Int, val registerNs: Long)
}

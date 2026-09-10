package top.wcpe.taboolib.ioc.inject

import taboolib.common.LifeCycle
import taboolib.common.platform.Awake
import taboolib.common.platform.Platform
import taboolib.common.platform.PlatformSide
import taboolib.common.platform.function.debug
import taboolib.common.platform.function.registerLifeCycleTask
import taboolib.common.platform.function.warning
import top.wcpe.taboolib.ioc.annotation.Lazy
import top.wcpe.taboolib.ioc.annotation.Named
import top.wcpe.taboolib.ioc.annotation.Resource
import top.wcpe.taboolib.ioc.annotation.Value
import top.wcpe.taboolib.ioc.bean.BeanContainer
import top.wcpe.taboolib.ioc.util.KotlinPropertyAnnotations.findAnnotation
import top.wcpe.taboolib.ioc.util.KotlinPropertyAnnotations.hasAnnotation
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import taboolib.common.Inject as TabooLibInject

/**
 * object 类自动注入器
 * 扫描 object 类中带有 @Inject、@Resource 等注解的字段并自动注入
 */
@TabooLibInject
object ObjectInjector {

    // 存储需要注入的 object 类
    private val objectClasses = linkedSetOf<Class<*>>()
    // 存储需要注入的 companion object 持有类（外部类）
    private val companionClasses = linkedSetOf<Class<*>>()

    /**
     * A-P1-10：注入失败warning 的按类名聚合计数器。
     *
     * 首次遇到某类名失败时完整输出 warning（不再无限流刷屏），
     * 后续同类名失败仅累加计数；注入结束时对计数 > 1 的类名输出一条聚合提示。
     * **不降级为 debug**：字段静默为 null 的代价是运行期 NPE，必须让运维看得到。
     */
    private val failureCounts = ConcurrentHashMap<String, AtomicInteger>()
    private val fullWarnedClasses = ConcurrentHashMap.newKeySet<String>()

    /**
     * 在 LOAD 阶段收集 object 类，在 ENABLE 阶段容器初始化后注入字段
     */
    @Awake(LifeCycle.LOAD)
    fun collectObjectClasses() {
        val start = System.nanoTime()
        objectClasses.clear()
        companionClasses.clear()
        for (javaClass in top.wcpe.taboolib.ioc.scan.getRunningClassesInJar()) {
            // 平台门控：错误平台的宿主在收集阶段即跳过，根本不去反射它（见 isPlatformMatched）。
            if (!isPlatformMatched(javaClass)) {
                debug("[IoC] 平台门控跳过 ${javaClass.name}（@PlatformSide 不含当前平台 ${Platform.CURRENT}）")
                continue
            }
            if (requiresCompanionInjection(javaClass)) {
                companionClasses += javaClass
            } else if (requiresObjectInjection(javaClass)) {
                objectClasses += javaClass
            }
        }
        val ms = (System.nanoTime() - start) / 1_000_000.0
        debug("[IoC] 收集到 ${objectClasses.size} 个待注入 object，${companionClasses.size} 个 companion object，耗时 ${"%.2f".format(ms)}ms")
        registerLifeCycleTask(LifeCycle.ENABLE, -90, Runnable {
            injectObjectFields()
        })
    }

    /**
     * 平台门控：宿主类标了 @PlatformSide 且其平台集合不含当前运行平台时返回 false（应跳过收集）；
     * 未标 @PlatformSide 视为全平台，返回 true。
     *
     * 必要性：本注入器经 [top.wcpe.taboolib.ioc.scan.getRunningClassesInJar] 直接读 TabooLib 的
     * runningClassMapInJar，刻意绕过 TabooLib 逐类 ClassVisitor（避免包名含 ".taboolib." 被误判为
     * 内部类而跳过），因而丢失了 TabooLib 自带的 @PlatformSide 过滤——错误平台的宿主（如 Bukkit 上
     * 标 @PlatformSide(BUNGEE) 的事件监听器）若被收集，注入时反射其 declaredMethods 会解析跨平台类
     * 入参（如 Bungee PostLoginEvent），当前平台缺失即抛 NoClassDefFoundError、崩掉整个插件 enable。
     * 在收集源头按平台跳过即根治；读注解只访问常量池、不解析方法签名，故安全。注入路径仍保留对
     * NoClassDefFoundError 的防御性捕获，兜底未标注解却引用缺失类的情形。
     */
    private fun isPlatformMatched(clazz: Class<*>): Boolean {
        return try {
            val side = clazz.getAnnotation(PlatformSide::class.java) ?: return true
            side.value.contains(Platform.CURRENT)
        } catch (e: Throwable) {
            // 读注解异常不擅自跳过，交由注入路径的防御性捕获兜底。
            true
        }
    }

    /**
     * 在容器初始化后自动注入 object 类的字段
     */
    private fun injectObjectFields() {
        if (!BeanContainer.initialized) return

        failureCounts.clear()
        fullWarnedClasses.clear()

        val start = System.nanoTime()
        var injected = 0
        for (clazz in objectClasses) {
            try {
                val bean = getObjectInstance(clazz) ?: continue
                val objStart = System.nanoTime()
                injectObject(bean, clazz)
                val objMs = (System.nanoTime() - objStart) / 1_000_000.0
                injected++
                debug("[IoC] object 注入完成: ${clazz.simpleName}，耗时 ${"%.2f".format(objMs)}ms")
            } catch (e: Throwable) {
                // 用 Throwable 而非 Exception：注入单个类时若触发 NoClassDefFoundError 等 Error
                // （如平台缺失类），跳过该类即可，绝不让其冒泡崩掉整个插件 enable。
                // 用 warning 而非 debug：字段静默为 null 的代价是运行期 NPE，必须让运维看得到。
                reportFailure(clazz.name, "object", e)
            }
        }
        // 注入 companion object 字段（字段在外部类上）
        for (outerClass in companionClasses) {
            try {
                // getCompanionInstance 仅做存在性校验：companion backing field 是外部类的 static 字段，
                // 注入用 field.set(null, ...) 完成，无需 companion 实例本身。
                if (getCompanionInstance(outerClass) == null) continue
                val objStart = System.nanoTime()
                injectCompanionFields(outerClass)
                val objMs = (System.nanoTime() - objStart) / 1_000_000.0
                injected++
                debug("[IoC] companion object 注入完成: ${outerClass.simpleName}，耗时 ${"%.2f".format(objMs)}ms")
            } catch (e: Throwable) {
                reportFailure(outerClass.name, "companion object", e)
            }
        }

        flushAggregatedFailures()

        val totalMs = (System.nanoTime() - start) / 1_000_000.0
        debug("[IoC] object 字段注入完成，共 $injected 个 object，总耗时 ${"%.2f".format(totalMs)}ms")
    }

    /**
     * A-P1-10：按类名聚合失败 warning。
     *
     * - 首次遇到某类名：输出完整 warning（含异常详情），让运维第一时间看到根因。
     * - 后续同类名：仅累加计数，不再逐次输出（避免多端平台缺失类导致启动刷屏）。
     * - 注入结束时：[flushAggregatedFailures] 对计数 > 1 的类名输出一条聚合提示。
     *
     * 绝不降级为 debug：字段静默为 null 会在运行期产生 NPE，必须保持 warning 可见。
     */
    private fun reportFailure(className: String, kind: String, error: Throwable) {
        failureCounts.computeIfAbsent(className) { AtomicInteger(0) }.incrementAndGet()
        if (fullWarnedClasses.add(className)) {
            warning("[IoC] $kind 注入失败（已跳过该类，不影响其他）: $className - $error")
        }
    }

    /**
     * A-P1-10：注入结束时输出聚合统计。
     */
    private fun flushAggregatedFailures() {
        var aggregatedKinds = 0
        var aggregatedTotal = 0
        for ((className, count) in failureCounts) {
            val n = count.get()
            if (n > 1) {
                aggregatedKinds++
                aggregatedTotal += n
                debug("[IoC] 同类失败 N 次，已聚合: $className 失败 $n 次（首次已输出完整 warning）")
            }
        }
        if (aggregatedKinds > 0) {
            warning("[IoC] object 注入失败汇总: 共 $aggregatedKinds 个类发生重复失败（合计 $aggregatedTotal 次），首次失败详情已在上方输出，重复项已按类名聚合")
        }
    }

    /**
     * 检查外部类是否持有 companion object 且其中有需要注入的字段。
     *
     * Kotlin companion object 属性（无论是否 @JvmField）的 backing field 都编译到**外部类的静态字段**上：
     * - 带 @JvmField：外部类 static field，$annotations 在 Companion 类
     * - 不带 @JvmField：外部类 static field（__JvmField 之外的同名静态字段），$annotations 在 Companion 类
     * 因此注入统一走 `field.set(null, ...)`（见 injectSingleField / injectCompanionFields）。
     */
    private fun requiresCompanionInjection(clazz: Class<*>): Boolean {
        return try {
            // 外部类必须有名为 "Companion" 的静态字段
            val companionField = try {
                clazz.getDeclaredField("Companion")
            } catch (e: NoSuchFieldException) {
                return false
            }
            // 该字段类型必须是 clazz 的内部类
            val companionClass = companionField.type
            if (companionClass.enclosingClass != clazz) return false
            // backing field（含 @JvmField 和非 @JvmField）均在外部类的静态字段上
            clazz.declaredFields.any { field ->
                field.name != "Companion" &&
                    java.lang.reflect.Modifier.isStatic(field.modifiers) && (
                    field.hasAnnotation(top.wcpe.taboolib.ioc.annotation.Inject::class.java) ||
                        field.findAnnotation(Resource::class.java) != null
                    )
            }
        } catch (e: NoClassDefFoundError) {
            debug("[IoC] 跳过 companion 类 ${clazz.name}，缺少依赖: ${e.message}")
            false
        }
    }

    /**
     * 获取外部类的 companion object 实例
     */
    private fun getCompanionInstance(outerClass: Class<*>): Any? {
        return try {
            val companionField = outerClass.getDeclaredField("Companion")
            companionField.isAccessible = true
            companionField.get(null)
        } catch (e: NoSuchFieldException) {
            null
        }
    }

    /**
     * 注入 companion object 的字段。
     *
     * 无论是否有 @JvmField，backing field 都在外部类的静态字段上，因此 `field.set(null, ...)` 正确。
     * （C-P2-15：原先的 companionInstance 参数是死参数——注入目标恒为 null——已移除。）
     * $annotations 方法在 Companion 类，由 KotlinPropertyAnnotations 负责跨类查找。
     */
    private fun injectCompanionFields(outerClass: Class<*>) {
        for (field in outerClass.declaredFields) {
            if (field.name == "Companion") continue
            if (!java.lang.reflect.Modifier.isStatic(field.modifiers)) continue
            injectSingleField(field, null)
        }
    }

    private fun injectSingleField(field: java.lang.reflect.Field, target: Any?) {
        // C-P2-18：object/companion 路径也要处理 @Value，与 @Component class 路径语义对齐。
        val valueAnnotation = field.getAnnotation(Value::class.java)
            ?: field.findAnnotation(Value::class.java)
        if (valueAnnotation != null) {
            val resolved = ValueResolver.resolve(valueAnnotation.value, field.type)
            if (resolved != null) {
                field.isAccessible = true
                field.set(target, resolved)
                debug("[IoC] @Value 注入 object/companion 字段: ${field.declaringClass.simpleName}.${field.name}")
            }
            return
        }

        val inject = field.hasAnnotation(top.wcpe.taboolib.ioc.annotation.Inject::class.java)
        val resource = field.findAnnotation(Resource::class.java)
        val named = field.findAnnotation(Named::class.java)
        val lazy = field.findAnnotation(Lazy::class.java)

        if (!inject && resource == null) return

        val nameQualifier = when {
            resource != null && resource.name.isNotEmpty() -> resource.name
            named != null && named.value.isNotEmpty() -> named.value
            else -> null
        }

        if (lazy?.value == true) {
            val companionOrOuter = target ?: return
            BeanContainer.injectLazyObjectField(companionOrOuter, field.type, nameQualifier, field)
        } else {
            val value = BeanContainer.getBean(field.type, nameQualifier)
            if (value != null) {
                field.isAccessible = true
                field.set(target, value)
                debug("[IoC] 自动注入 companion object 字段: ${field.declaringClass.simpleName}.${field.name}")
            }
        }
    }

    private fun requiresObjectInjection(clazz: Class<*>): Boolean {
        return try {
            if (!isObjectOrCompanionClass(clazz)) return false
            clazz.declaredFields.any { field ->
                field.name != "INSTANCE" && isInjectableField(field)
            }
        } catch (e: NoClassDefFoundError) {
            debug("[IoC] 跳过类 ${clazz.name}，缺少依赖: ${e.message}")
            false
        }
    }

    /**
     * C-P2-18：object 路径的可注入字段含 @Inject/@Resource 与 @Value。
     */
    private fun isInjectableField(field: java.lang.reflect.Field): Boolean {
        return field.hasAnnotation(top.wcpe.taboolib.ioc.annotation.Inject::class.java) ||
            field.findAnnotation(Resource::class.java) != null ||
            field.getAnnotation(Value::class.java) != null ||
            field.findAnnotation(Value::class.java) != null
    }

    /**
     * 检查是否为 Kotlin object 类（含 companion object）
     */
    private fun isObjectOrCompanionClass(clazz: Class<*>): Boolean {
        return try {
            clazz.getDeclaredField("INSTANCE") != null
        } catch (e: NoSuchFieldException) {
            false
        } catch (e: NoClassDefFoundError) {
            false
        }
    }

    private fun getObjectInstance(clazz: Class<*>): Any? {
        return try {
            val instanceField = clazz.getDeclaredField("INSTANCE")
            instanceField.isAccessible = true
            instanceField.get(null)
        } catch (e: NoSuchFieldException) {
            null
        }
    }

    /**
     * 注入指定 object 类的字段
     */
    fun injectObject(obj: Any, clazz: Class<*>) {
        for (field in clazz.declaredFields) {
            // 跳过 INSTANCE 字段
            if (field.name == "INSTANCE") continue

            // C-P2-18：object 路径也要处理 @Value，与 @Component class 路径语义对齐。
            val valueAnnotation = field.getAnnotation(Value::class.java)
                ?: field.findAnnotation(Value::class.java)
            if (valueAnnotation != null) {
                val resolved = ValueResolver.resolve(valueAnnotation.value, field.type)
                if (resolved != null) {
                    field.isAccessible = true
                    field.set(obj, resolved)
                    debug("[IoC] @Value 注入 object 字段: ${clazz.simpleName}.${field.name}")
                }
                continue
            }

            val inject = field.hasAnnotation(top.wcpe.taboolib.ioc.annotation.Inject::class.java)
            val resource = field.findAnnotation(Resource::class.java)
            val named = field.findAnnotation(Named::class.java)
            val lazy = field.findAnnotation(Lazy::class.java)

            if (!inject && resource == null) continue

            val nameQualifier = when {
                resource != null && resource.name.isNotEmpty() -> resource.name
                named != null && named.value.isNotEmpty() -> named.value
                else -> null
            }

            if (lazy?.value == true) {
                // 延迟注入：通过 FieldInjector 创建代理
                BeanContainer.injectLazyObjectField(obj, field.type, nameQualifier, field)
            } else {
                val value = BeanContainer.getBean(field.type, nameQualifier)
                if (value != null) {
                    field.isAccessible = true
                    field.set(obj, value)
                    debug("[IoC] 自动注入 object 字段: ${clazz.simpleName}.${field.name}")
                }
            }
        }
    }
}

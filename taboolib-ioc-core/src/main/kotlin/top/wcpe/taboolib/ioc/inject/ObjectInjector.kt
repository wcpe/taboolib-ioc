package top.wcpe.taboolib.ioc.inject

import taboolib.common.LifeCycle
import taboolib.common.platform.Awake
import taboolib.common.platform.function.debug
import taboolib.common.platform.function.registerLifeCycleTask
import top.wcpe.taboolib.ioc.annotation.Lazy
import top.wcpe.taboolib.ioc.annotation.Named
import top.wcpe.taboolib.ioc.annotation.Resource
import top.wcpe.taboolib.ioc.bean.BeanContainer
import top.wcpe.taboolib.ioc.util.KotlinPropertyAnnotations.findAnnotation
import top.wcpe.taboolib.ioc.util.KotlinPropertyAnnotations.hasAnnotation
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
     * 在 LOAD 阶段收集 object 类，在 ENABLE 阶段容器初始化后注入字段
     */
    @Awake(LifeCycle.LOAD)
    fun collectObjectClasses() {
        val start = System.nanoTime()
        objectClasses.clear()
        companionClasses.clear()
        for (javaClass in top.wcpe.taboolib.ioc.scan.getRunningClassesInJar()) {
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
     * 在容器初始化后自动注入 object 类的字段
     */
    private fun injectObjectFields() {
        if (!BeanContainer.initialized) return

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
            } catch (e: Exception) {
                debug("[IoC] object 注入失败: ${clazz.name} - ${e.message}")
            }
        }
        // 注入 companion object 字段（字段在外部类上）
        for (outerClass in companionClasses) {
            try {
                val companionInstance = getCompanionInstance(outerClass) ?: continue
                val objStart = System.nanoTime()
                injectCompanionFields(companionInstance, outerClass)
                val objMs = (System.nanoTime() - objStart) / 1_000_000.0
                injected++
                debug("[IoC] companion object 注入完成: ${outerClass.simpleName}，耗时 ${"%.2f".format(objMs)}ms")
            } catch (e: Exception) {
                debug("[IoC] companion object 注入失败: ${outerClass.name} - ${e.message}")
            }
        }

        val totalMs = (System.nanoTime() - start) / 1_000_000.0
        debug("[IoC] object 字段注入完成，共 $injected 个 object，总耗时 ${"%.2f".format(totalMs)}ms")
    }

    /**
     * 检查外部类是否持有 companion object 且其中有需要注入的字段。
     *
     * Kotlin companion object 属性有两种编译形式：
     * 1. 带 @JvmField：backing field 是外部类的静态字段，$annotations 在 Companion 类
     * 2. 不带 @JvmField：backing field 是 Companion 类的实例字段，$annotations 也在 Companion 类
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
     * 无论是否有 @JvmField，backing field 都在外部类的静态字段上。
     * $annotations 方法在 Companion 类，由 KotlinPropertyAnnotations 负责跨类查找。
     */
    private fun injectCompanionFields(companionInstance: Any, outerClass: Class<*>) {
        for (field in outerClass.declaredFields) {
            if (field.name == "Companion") continue
            if (!java.lang.reflect.Modifier.isStatic(field.modifiers)) continue
            injectSingleField(field, null)
        }
    }

    private fun injectSingleField(field: java.lang.reflect.Field, target: Any?) {
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
                field.name != "INSTANCE" && (
                    field.hasAnnotation(top.wcpe.taboolib.ioc.annotation.Inject::class.java) ||
                        field.findAnnotation(Resource::class.java) != null
                    )
            }
        } catch (e: NoClassDefFoundError) {
            debug("[IoC] 跳过类 ${clazz.name}，缺少依赖: ${e.message}")
            false
        }
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

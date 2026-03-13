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

    /**
     * 在 LOAD 阶段收集 object 类，在 ENABLE 阶段容器初始化后注入字段
     */
    @Awake(LifeCycle.LOAD)
    fun collectObjectClasses() {
        val start = System.nanoTime()
        objectClasses.clear()
        for (javaClass in top.wcpe.taboolib.ioc.scan.getRunningClassesInJar()) {
            if (!requiresObjectInjection(javaClass)) continue
            objectClasses += javaClass
        }
        val ms = (System.nanoTime() - start) / 1_000_000.0
        debug("[IoC] 收集到 ${objectClasses.size} 个待注入 object，耗时 ${"%.2f".format(ms)}ms")
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
        val totalMs = (System.nanoTime() - start) / 1_000_000.0
        debug("[IoC] object 字段注入完成，共 $injected 个 object，总耗时 ${"%.2f".format(totalMs)}ms")
    }

    private fun requiresObjectInjection(clazz: Class<*>): Boolean {
        return try {
            if (!isObjectClass(clazz)) return false
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
     * 检查是否为 Kotlin object 类
     */
    private fun isObjectClass(clazz: Class<*>): Boolean {
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

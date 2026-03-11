package top.wcpe.taboolib.ioc.inject

import taboolib.common.LifeCycle
import taboolib.common.io.runningClassMapInJar
import taboolib.common.platform.Awake
import taboolib.common.platform.function.debug
import taboolib.common.platform.function.registerLifeCycleTask
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
     * 在 ENABLE 阶段收集 object 类
     */
    @Awake(LifeCycle.ENABLE)
    fun collectObjectClasses() {
        objectClasses.clear()
        for (reflexClass in runningClassMapInJar.values) {
            val javaClass = reflexClass.toClass() ?: continue
            if (!requiresObjectInjection(javaClass)) continue
            objectClasses += javaClass
        }
        debug("[IoC] 收集到 ${objectClasses.size} 个待注入 object")
        registerLifeCycleTask(LifeCycle.ACTIVE, -90, Runnable {
            injectObjectFields()
        })
    }

    /**
     * 在容器初始化后自动注入 object 类的字段
     */
    private fun injectObjectFields() {
        if (!BeanContainer.initialized) return

        for (clazz in objectClasses) {
            try {
                val bean = getObjectInstance(clazz) ?: continue
                injectObject(bean, clazz)
            } catch (e: Exception) {
                debug("[IoC] object 注入失败: ${clazz.name} - ${e.message}")
            }
        }
    }

    private fun requiresObjectInjection(clazz: Class<*>): Boolean {
        if (!isObjectClass(clazz)) return false
        return clazz.declaredFields.any { field ->
            field.name != "INSTANCE" && (
                field.hasAnnotation(top.wcpe.taboolib.ioc.annotation.Inject::class.java) ||
                    field.findAnnotation(Resource::class.java) != null
                )
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

            if (!inject && resource == null) continue

            val nameQualifier = when {
                resource != null && resource.name.isNotEmpty() -> resource.name
                named != null && named.value.isNotEmpty() -> named.value
                else -> null
            }

            val value = BeanContainer.getBean(field.type, nameQualifier)

            if (value != null) {
                field.isAccessible = true
                field.set(obj, value)
                debug("[IoC] 自动注入 object 字段: ${clazz.simpleName}.${field.name}")
            }
        }
    }
}

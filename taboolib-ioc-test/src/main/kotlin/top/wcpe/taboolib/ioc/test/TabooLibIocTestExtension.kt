package top.wcpe.taboolib.ioc.test

import java.lang.reflect.Modifier
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.TestInstancePostProcessor

/**
 * 为 @TabooLibIocTest 提供自动引导与注入能力。
 */
class TabooLibIocTestExtension : BeforeAllCallback, AfterAllCallback, TestInstancePostProcessor {

    companion object {
        private val NAMESPACE = ExtensionContext.Namespace.create(TabooLibIocTestExtension::class.java)
        private const val KEY_CONTEXT = "taboolib_ioc_test_context"
    }

    override fun beforeAll(context: ExtensionContext) {
        val annotation = context.requiredTestClass.getAnnotation(TabooLibIocTest::class.java)
            ?: return

        val testContext = TabooLibIocTestContext()
        val componentClasses = annotation.components.map { it.java }.toTypedArray()
        testContext.bootstrap(
            targetLifeCycle = annotation.targetLifeCycle,
            invokePostEnable = annotation.invokePostEnable,
            observable = annotation.observable,
            enablePrimitiveBootstrap = annotation.enablePrimitiveBootstrap,
            *componentClasses
        )

        context.getStore(NAMESPACE).put(KEY_CONTEXT, testContext)
    }

    override fun postProcessTestInstance(testInstance: Any, context: ExtensionContext) {
        val testContext = context.getStore(NAMESPACE).get(KEY_CONTEXT, TabooLibIocTestContext::class.java) ?: return

        injectAnnotatedFields(testInstance, testContext)
    }

    override fun afterAll(context: ExtensionContext) {
        val testContext = context.getStore(NAMESPACE).remove(KEY_CONTEXT, TabooLibIocTestContext::class.java)
        testContext?.shutdown()
    }

    private fun injectAnnotatedFields(testInstance: Any, testContext: TabooLibIocTestContext) {
        var current: Class<*>? = testInstance.javaClass
        while (current != null && current != Any::class.java) {
            current.declaredFields.forEach { field ->
                if (!field.isAnnotationPresent(IocAutowired::class.java)) {
                    return@forEach
                }
                if (Modifier.isStatic(field.modifiers)) {
                    return@forEach
                }

                field.isAccessible = true
                val value = resolveInjectValue(field.type, testContext)
                    ?: error("无法为字段 ${field.name} 注入类型 ${field.type.name}")
                field.set(testInstance, value)
            }
            current = current.superclass
        }
    }

    private fun resolveInjectValue(type: Class<*>, testContext: TabooLibIocTestContext): Any? {
        if (type == TabooLibIocTestContext::class.java) {
            return testContext
        }
        return testContext.getBean(type)
    }
}
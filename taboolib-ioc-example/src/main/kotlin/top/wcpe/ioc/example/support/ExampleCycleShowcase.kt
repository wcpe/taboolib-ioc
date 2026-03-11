package top.wcpe.ioc.example.support

import top.wcpe.taboolib.ioc.annotation.Component
import top.wcpe.taboolib.ioc.annotation.Inject
import top.wcpe.taboolib.ioc.bean.BeanRegistry
import top.wcpe.taboolib.ioc.bean.BeanContainer
import top.wcpe.taboolib.ioc.cycle.CircularDependencyException
import top.wcpe.taboolib.ioc.cycle.CycleDetector
import top.wcpe.taboolib.ioc.cycle.CycleResolver
import top.wcpe.taboolib.ioc.inject.ConstructorResolver
import top.wcpe.taboolib.ioc.inject.FieldInjector
import top.wcpe.taboolib.ioc.inject.Injector
import top.wcpe.taboolib.ioc.lifecycle.LifecycleManager
import top.wcpe.taboolib.ioc.scan.ClassScanner

object ExampleCycleShowcase {

    fun fieldCycleSummary(): String {
        val left = BeanContainer.getBean(ExampleFieldCycleLeft::class.java) ?: return "missing"
        val right = BeanContainer.getBean(ExampleFieldCycleRight::class.java) ?: return "missing"
        return "${left.snapshot()}|${right.snapshot()}"
    }

    fun constructorCycleSummary(): String {
        val context = LocalCycleContext()
        return try {
            context.register(ExampleConstructorCycleLeft::class.java)
            context.register(ExampleConstructorCycleRight::class.java)
            context.lifecycleManager.initialize()
            "missing"
        } catch (ex: CircularDependencyException) {
            ex.dependencyChain.joinToString(" -> ")
        }
    }
}

@Component
class ExampleFieldCycleLeft {

    @Inject
    lateinit var right: ExampleFieldCycleRight

    fun snapshot(): String = "left->${right.label()}"

    fun label(): String = "left"
}

@Component
class ExampleFieldCycleRight {

    @Inject
    lateinit var left: ExampleFieldCycleLeft

    fun snapshot(): String = "right->${left.label()}"

    fun label(): String = "right"
}

private class LocalCycleContext {

    private val registry = BeanRegistry()
    private val cycleResolver = CycleResolver()
    private val cycleDetector = CycleDetector()
    private val constructorResolver = ConstructorResolver()
    private val fieldInjector = FieldInjector(registry) { type, name ->
        resolveExistingBean(type, name)
    }
    private val injector = Injector(registry, cycleResolver, fieldInjector)
    val lifecycleManager = LifecycleManager(registry, cycleResolver, injector, cycleDetector)
    private val scanner = ClassScanner(registry, constructorResolver)

    fun register(clazz: Class<*>) {
        val definition = scanner.scan(clazz) ?: error("scan failed for ${clazz.name}")
        registry.register(definition)
    }

    private fun resolveExistingBean(type: Class<*>, name: String?): Any? {
        if (name != null) {
            cycleResolver.getSingleton(name)?.let { instance ->
                if (type.isInstance(instance)) {
                    return instance
                }
            }
        }

        val definition = if (name != null) {
            registry.getByName(name)
        } else {
            registry.getPrimaryByType(type)
        } ?: return null

        return cycleResolver.getSingleton(definition.name)?.takeIf(type::isInstance)
    }
}

@Component
private class ExampleConstructorCycleLeft @Inject constructor(
    val right: ExampleConstructorCycleRight
)

@Component
private class ExampleConstructorCycleRight @Inject constructor(
    val left: ExampleConstructorCycleLeft
)
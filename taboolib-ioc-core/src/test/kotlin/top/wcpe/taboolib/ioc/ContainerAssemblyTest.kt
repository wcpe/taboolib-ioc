package top.wcpe.taboolib.ioc

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import top.wcpe.taboolib.ioc.annotation.Component
import top.wcpe.taboolib.ioc.annotation.Inject
import top.wcpe.taboolib.ioc.annotation.Named
import top.wcpe.taboolib.ioc.annotation.PostConstruct
import top.wcpe.taboolib.ioc.annotation.Resource
import top.wcpe.taboolib.ioc.annotation.Service
import top.wcpe.taboolib.ioc.bean.BeanRegistry
import top.wcpe.taboolib.ioc.cycle.CircularDependencyException
import top.wcpe.taboolib.ioc.cycle.CycleDetector
import top.wcpe.taboolib.ioc.cycle.CycleResolver
import top.wcpe.taboolib.ioc.inject.ConstructorResolver
import top.wcpe.taboolib.ioc.inject.FieldInjector
import top.wcpe.taboolib.ioc.inject.Injector
import top.wcpe.taboolib.ioc.lifecycle.LifecycleManager
import top.wcpe.taboolib.ioc.scan.ClassScanner

class ContainerAssemblyTest {

    @Test
    fun `scanner should capture constructor and injection metadata without creating instances`() {
        MetadataDrivenService.constructedCount = 0
        val context = TestContext()

        val definition = context.scan(MetadataDrivenService::class.java)

        assertEquals(0, MetadataDrivenService.constructedCount)
        assertEquals(1, definition.constructorParameters.size)
        assertEquals("alphaGateway", definition.constructorParameters.single().nameQualifier)
        assertEquals(1, definition.injectFields.size)
        assertEquals("betaGateway", definition.injectFields.single().nameQualifier)
        assertEquals(1, definition.injectMethods.size)
        assertEquals("alphaGateway", definition.injectMethods.single().parameters.single().nameQualifier)
        assertEquals(3, definition.dependencies.size)
    }

    @Test
    fun `lifecycle should assemble beans in two phases and run post construct after injection`() {
        MetadataDrivenService.constructedCount = 0
        val context = TestContext()

        context.register(AlphaGateway::class.java)
        context.register(BetaGateway::class.java)
        context.register(MetadataDrivenService::class.java)

        assertEquals(0, MetadataDrivenService.constructedCount)

        context.lifecycleManager.initialize()

        val service = context.getSingleton("metadataDrivenService") as MetadataDrivenService
        assertNotNull(service)
        assertEquals(1, MetadataDrivenService.constructedCount)
        assertEquals("alpha|beta|alpha", service.snapshot)
        assertEquals("beta", service.fieldGateway.id())
        assertEquals("alpha", service.methodGateway.id())
    }

    @Test
    fun `lifecycle should resolve field cycles during populate phase`() {
        FieldCycleLeft.postConstructSawRight = false
        FieldCycleRight.postConstructSawLeft = false
        val context = TestContext()

        context.register(FieldCycleLeft::class.java)
        context.register(FieldCycleRight::class.java)

        context.lifecycleManager.initialize()

        val left = context.getSingleton("fieldCycleLeft") as FieldCycleLeft
        val right = context.getSingleton("fieldCycleRight") as FieldCycleRight

        assertSame(right, left.right)
        assertSame(left, right.left)
        assertEquals(true, FieldCycleLeft.postConstructSawRight)
        assertEquals(true, FieldCycleRight.postConstructSawLeft)
    }

    @Test
    fun `lifecycle should reject constructor dependency cycles`() {
        val context = TestContext()

        context.register(ConstructorCycleLeft::class.java)
        context.register(ConstructorCycleRight::class.java)

        assertThrows(CircularDependencyException::class.java) {
            context.lifecycleManager.initialize()
        }
    }
}

private class TestContext {

    val registry = BeanRegistry()
    private val cycleResolver = CycleResolver()
    private val cycleDetector = CycleDetector()
    private val constructorResolver = ConstructorResolver()
    private val fieldInjector = FieldInjector(registry) { type, name ->
        resolveExistingBean(type, name)
    }
    private val injector = Injector(registry, cycleResolver, fieldInjector)
    val lifecycleManager = LifecycleManager(registry, cycleResolver, injector, cycleDetector)
    private val scanner = ClassScanner(constructorResolver)

    fun scan(clazz: Class<*>) = scanner.scan(clazz) ?: error("scan failed for ${clazz.name}")

    fun register(clazz: Class<*>) {
        registry.register(scan(clazz))
    }

    fun getSingleton(name: String): Any? {
        return cycleResolver.getSingleton(name)
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

private interface TestGateway {
    fun id(): String
}

@Component("alphaGateway")
private class AlphaGateway : TestGateway {

    override fun id(): String = "alpha"
}

@Component("betaGateway")
private class BetaGateway : TestGateway {

    override fun id(): String = "beta"
}

@Service
private class MetadataDrivenService @Inject constructor(
    @Named("alphaGateway")
    private val constructorGateway: TestGateway
) {

    companion object {
        var constructedCount = 0
    }

    init {
        constructedCount++
    }

    @Inject
    @Named("betaGateway")
    lateinit var fieldGateway: TestGateway

    lateinit var methodGateway: TestGateway
    var snapshot: String = ""

    @Resource(name = "alphaGateway")
    fun bindMethodGateway(gateway: TestGateway) {
        methodGateway = gateway
    }

    @PostConstruct
    fun afterInject() {
        snapshot = listOf(
            constructorGateway.id(),
            fieldGateway.id(),
            methodGateway.id()
        ).joinToString("|")
    }
}

@Component
private class FieldCycleLeft {

    companion object {
        var postConstructSawRight = false
    }

    @Inject
    lateinit var right: FieldCycleRight

    @PostConstruct
    fun afterInject() {
        postConstructSawRight = this::right.isInitialized
    }
}

@Component
private class FieldCycleRight {

    companion object {
        var postConstructSawLeft = false
    }

    @Inject
    lateinit var left: FieldCycleLeft

    @PostConstruct
    fun afterInject() {
        postConstructSawLeft = this::left.isInitialized
    }
}

@Component
private class ConstructorCycleLeft @Inject constructor(
    val right: ConstructorCycleRight
)

@Component
private class ConstructorCycleRight @Inject constructor(
    val left: ConstructorCycleLeft
)
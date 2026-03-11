package top.wcpe.taboolib.ioc

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import top.wcpe.taboolib.ioc.annotation.Component
import top.wcpe.taboolib.ioc.annotation.Inject
import top.wcpe.taboolib.ioc.annotation.Named
import top.wcpe.taboolib.ioc.annotation.PostConstruct
import top.wcpe.taboolib.ioc.annotation.Resource
import top.wcpe.taboolib.ioc.bean.BeanContainer
import top.wcpe.taboolib.ioc.cycle.CircularDependencyException

class BeanContainerIntegrationTest {

    @AfterEach
    fun cleanup() {
        BeanContainer.resetForTesting()
        ContainerCycleAwareService.constructed = 0
        ContainerCycleAwareService.postConstructSnapshot = ""
        ContainerFieldCycleLeft.ready = false
        ContainerFieldCycleRight.ready = false
        ContainerMaskedConsumer.ready = false
    }

    @Test
    fun `bean container should initialize scanned classes and expose queries`() {
        registerScanned(ContainerPrimaryGateway::class.java)
        registerScanned(ContainerSecondaryGateway::class.java)
        registerScanned(ContainerCycleAwareService::class.java)
        registerScanned(ContainerFieldTargetNamedService::class.java)
        registerScanned(ContainerMaskedStore::class.java)
        registerScanned(ContainerMaskedSettings::class.java)
        registerScanned(ContainerMaskedServiceImpl::class.java)
        registerScanned(ContainerMaskedConsumer::class.java)
        BeanContainer.registerBean("containerManualValue", ContainerManualValue("manual-ready"))

        BeanContainer.initialize()

        val serviceByType = BeanContainer.getBean(ContainerCycleAwareService::class.java)
        val serviceByName = BeanContainer.getBean(ContainerCycleAwareService::class.java, "containerCycleAwareService")
        val fieldTargetNamedService = BeanContainer.getBean(ContainerFieldTargetNamedService::class.java)
        val maskedContract = BeanContainer.getBean(ContainerMaskedContract::class.java)
        val maskedConsumer = BeanContainer.getBean(ContainerMaskedConsumer::class.java)
        val gatewayByName = BeanContainer.getBean(ContainerGateway::class.java, "primaryContainerGateway")
        val manualValue = BeanContainer.getBean(ContainerManualValue::class.java, "containerManualValue")
        val allGateways = BeanContainer.getBeansOfType(ContainerGateway::class.java)
            .map { it.id() }
            .sorted()

        assertNotNull(serviceByType)
        assertSame(serviceByType, serviceByName)
        assertEquals("primary", fieldTargetNamedService?.repositoryId())
        assertEquals("masked-store|masked-settings", maskedContract?.describe())
        assertEquals("masked-store|masked-settings", maskedConsumer?.service?.describe())
        assertEquals(true, ContainerMaskedConsumer.ready)
        assertEquals("primary", gatewayByName?.id())
        assertEquals("manual-ready", manualValue?.value)
        assertEquals(listOf("primary", "secondary"), allGateways)
        assertEquals(1, ContainerCycleAwareService.constructed)
        assertEquals("primary|secondary|true", ContainerCycleAwareService.postConstructSnapshot)
        assertEquals(true, BeanContainer.containsBean("containerManualValue"))
        assertEquals(true, BeanContainer.getBeanNames().contains("containerCycleAwareService"))
    }

    @Test
    fun `bean container should resolve field dependency cycles`() {
        registerScanned(ContainerFieldCycleLeft::class.java)
        registerScanned(ContainerFieldCycleRight::class.java)

        BeanContainer.initialize()

        val left = BeanContainer.getBean(ContainerFieldCycleLeft::class.java)
        val right = BeanContainer.getBean(ContainerFieldCycleRight::class.java)

        assertNotNull(left)
        assertNotNull(right)
        assertSame(right, left?.right)
        assertSame(left, right?.left)
        assertEquals(true, ContainerFieldCycleLeft.ready)
        assertEquals(true, ContainerFieldCycleRight.ready)
    }

    @Test
    fun `bean container should reject constructor dependency cycles`() {
        registerScanned(ContainerConstructorCycleLeft::class.java)
        registerScanned(ContainerConstructorCycleRight::class.java)

        val exception = assertThrows(CircularDependencyException::class.java) {
            BeanContainer.initialize()
        }

        assertFalse(BeanContainer.initialized)
        assertEquals(
            listOf(
                "containerConstructorCycleLeft",
                "containerConstructorCycleRight",
                "containerConstructorCycleLeft"
            ),
            exception.dependencyChain
        )
    }

    private fun registerScanned(clazz: Class<*>) {
        val definition = BeanContainer.getScanner().scan(clazz) ?: error("scan failed for ${clazz.name}")
        BeanContainer.getRegistry().register(definition)
    }
}

private interface ContainerGateway {
    fun id(): String
}

private data class ContainerManualValue(val value: String)

@Component("primaryContainerGateway")
private class ContainerPrimaryGateway : ContainerGateway {
    override fun id(): String = "primary"
}

@Component("secondaryContainerGateway")
private class ContainerSecondaryGateway : ContainerGateway {
    override fun id(): String = "secondary"
}

@Component
private class ContainerCycleAwareService @Inject constructor(
    @Named("primaryContainerGateway")
    private val constructorGateway: ContainerGateway
) {

    companion object {
        var constructed = 0
        var postConstructSnapshot = ""
    }

    init {
        constructed++
    }

    @Inject
    @Named("secondaryContainerGateway")
    lateinit var fieldGateway: ContainerGateway

    private var methodInjected = false

    @Resource(name = "primaryContainerGateway")
    fun bindMethodGateway(gateway: ContainerGateway) {
        methodInjected = gateway.id() == "primary"
    }

    @PostConstruct
    fun afterInject() {
        postConstructSnapshot = "${constructorGateway.id()}|${fieldGateway.id()}|$methodInjected"
    }
}

@Component
private class ContainerFieldTargetNamedService @Inject constructor(
    @field:Named("primaryContainerGateway")
    private val repository: ContainerGateway
) {

    fun repositoryId(): String = repository.id()
}

private interface ContainerMaskedStoreContract {
    fun source(): String
}

private interface ContainerMaskedSettingsContract {
    fun mode(): String
}

private interface ContainerMaskedContract {
    fun describe(): String
}

@Component("maskedStore")
private class ContainerMaskedStore : ContainerMaskedStoreContract {
    override fun source(): String = "masked-store"
}

@Component
private class ContainerMaskedSettings : ContainerMaskedSettingsContract {
    override fun mode(): String = "masked-settings"
}

@Component
private class ContainerMaskedServiceImpl @Inject constructor(
    @Named("maskedStore")
    private val repository: ContainerMaskedStoreContract,
    private val configProvider: ContainerMaskedSettingsContract
) : ContainerMaskedContract {

    override fun describe(): String {
        return "${repository.source()}|${configProvider.mode()}"
    }
}

@Component
private class ContainerMaskedConsumer {

    companion object {
        var ready = false
    }

    @Inject
    lateinit var service: ContainerMaskedContract
        private set

    @PostConstruct
    fun afterInject() {
        ready = this::service.isInitialized && service.describe() == "masked-store|masked-settings"
    }
}

@Component
private class ContainerFieldCycleLeft {

    companion object {
        var ready = false
    }

    @Inject
    lateinit var right: ContainerFieldCycleRight

    @PostConstruct
    fun afterInject() {
        ready = this::right.isInitialized
    }
}

@Component
private class ContainerFieldCycleRight {

    companion object {
        var ready = false
    }

    @Inject
    lateinit var left: ContainerFieldCycleLeft

    @PostConstruct
    fun afterInject() {
        ready = this::left.isInitialized
    }
}

@Component
private class ContainerConstructorCycleLeft @Inject constructor(
    val right: ContainerConstructorCycleRight
)

@Component
private class ContainerConstructorCycleRight @Inject constructor(
    val left: ContainerConstructorCycleLeft
)
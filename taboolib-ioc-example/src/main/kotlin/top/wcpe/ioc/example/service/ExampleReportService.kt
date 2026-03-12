package top.wcpe.ioc.example.service

import taboolib.common.platform.function.info
import top.wcpe.ioc.example.component.ExampleTextComponent
import top.wcpe.ioc.example.gateway.ExampleGateway
import top.wcpe.ioc.example.repository.ExampleUserRepository
import top.wcpe.taboolib.ioc.annotation.*

@Service
class ExampleReportService @Inject constructor(
    private val repository: ExampleUserRepository
) {

    @Inject
    @Named("wechatGateway")
    lateinit var auditGateway: ExampleGateway

    private lateinit var fallbackGateway: ExampleGateway
    private lateinit var textComponent: ExampleTextComponent
    private var postConstructInvoked = false
    private var postEnableInvoked = false

    @Resource(name = "alipayGateway")
    fun bindFallbackGateway(gateway: ExampleGateway) {
        fallbackGateway = gateway
    }

    @Inject
    fun bindTextComponent(textComponent: ExampleTextComponent) {
        this.textComponent = textComponent
    }

    @PostConstruct
    fun onInit() {
        postConstructInvoked = true
        info("ExampleReportService 初始化完成")
    }

    @PostEnable
    fun onEnable() {
        postEnableInvoked = true
        info("ExampleReportService PostEnable 回调")
    }

    @PreDestroy
    fun onDestroy() {
        info("ExampleReportService 销毁前回调")
    }

    fun buildCoreChecks(): List<String> {
        return listOf(
            textComponent.line("constructorInjection", repository.loadStatus()),
            textComponent.line("fieldNamedInjection", auditGateway.channel()),
            textComponent.line("methodResourceInjection", fallbackGateway.channel()),
            textComponent.line("methodInject", textComponent.javaClass.simpleName),
            textComponent.line("postConstruct", postConstructInvoked),
            textComponent.line("postEnable", postEnableInvoked)
        )
    }

    fun objectSummary(): String {
        return "${repository.loadStatus()}|${auditGateway.channel()}"
    }
}

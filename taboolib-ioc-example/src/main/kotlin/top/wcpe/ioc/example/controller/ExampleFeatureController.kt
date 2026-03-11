package top.wcpe.ioc.example.controller

import top.wcpe.taboolib.ioc.annotation.Controller
import top.wcpe.taboolib.ioc.annotation.Inject
import top.wcpe.taboolib.ioc.bean.BeanContainer
import top.wcpe.ioc.example.component.ExampleTextComponent
import top.wcpe.ioc.example.gateway.ExampleGateway
import top.wcpe.ioc.example.model.ExampleManualToken
import top.wcpe.ioc.example.service.ExampleReportService
import top.wcpe.ioc.example.support.ExampleObjectBridge

@Controller
class ExampleFeatureController @Inject constructor(
    private val reportService: ExampleReportService,
    private val textComponent: ExampleTextComponent
) {

    fun runAllChecks(): List<String> {
        val serviceByType = BeanContainer.getBean(ExampleReportService::class.java)
        val gatewayByName = BeanContainer.getBean(ExampleGateway::class.java, "wechatGateway")
        val allGateways = BeanContainer.getBeansOfType(ExampleGateway::class.java)
            .map { it.channel() }
            .sorted()
            .joinToString(",")
        val manualToken = BeanContainer
            .getBean(ExampleManualToken::class.java, "exampleManualToken")
            ?.value ?: "missing"
        val beanNames = BeanContainer.getBeanNames()
            .filter { it.startsWith("example") || it.endsWith("Gateway") }
            .sorted()
            .joinToString(",")

        return mutableListOf<String>().apply {
            addAll(reportService.buildCoreChecks())
            add(textComponent.line("getBeanByType", serviceByType?.javaClass?.simpleName ?: "missing"))
            add(textComponent.line("getBeanByName", gatewayByName?.channel() ?: "missing"))
            add(textComponent.line("getBeansOfType", allGateways))
            add(textComponent.line("containsBean", BeanContainer.containsBean("exampleManualToken")))
            add(textComponent.line("getBeanNames", beanNames))
            add(textComponent.line("registerBean", manualToken))
            add(textComponent.line("objectInjection", ExampleObjectBridge.snapshot()))
        }
    }
}

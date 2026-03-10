package top.wcpe.taboolib.ioc.example.support

import top.wcpe.taboolib.ioc.annotation.Inject
import top.wcpe.taboolib.ioc.annotation.Named
import top.wcpe.taboolib.ioc.example.gateway.ExampleGateway
import top.wcpe.taboolib.ioc.example.service.ExampleReportService

object ExampleObjectBridge {

    @Inject
    lateinit var reportService: ExampleReportService

    @Inject
    @Named("wechatGateway")
    lateinit var gateway: ExampleGateway

    fun snapshot(): String {
        return "${reportService.objectSummary()}|${gateway.channel()}"
    }
}

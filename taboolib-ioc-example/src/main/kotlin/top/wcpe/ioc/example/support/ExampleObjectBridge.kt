package top.wcpe.ioc.example.support

import top.wcpe.ioc.example.gateway.ExampleGateway
import top.wcpe.ioc.example.service.ExampleReportService
import top.wcpe.taboolib.ioc.annotation.Inject
import top.wcpe.taboolib.ioc.annotation.Named

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

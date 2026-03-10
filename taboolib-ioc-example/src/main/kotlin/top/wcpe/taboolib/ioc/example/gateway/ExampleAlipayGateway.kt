package top.wcpe.taboolib.ioc.example.gateway

import top.wcpe.taboolib.ioc.annotation.Component

@Component("alipayGateway")
class ExampleAlipayGateway : ExampleGateway {

    override fun channel(): String {
        return "alipay"
    }
}

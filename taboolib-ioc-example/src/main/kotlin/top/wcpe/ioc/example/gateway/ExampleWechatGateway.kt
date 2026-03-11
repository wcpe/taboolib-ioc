package top.wcpe.ioc.example.gateway

import top.wcpe.taboolib.ioc.annotation.Component

@Component("wechatGateway")
class ExampleWechatGateway : ExampleGateway {

    override fun channel(): String {
        return "wechat"
    }
}

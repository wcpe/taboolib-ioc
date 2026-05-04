package top.wcpe.taboolib.ioc.test.v12

import top.wcpe.taboolib.ioc.annotation.Inject
import top.wcpe.taboolib.ioc.annotation.Service
import top.wcpe.taboolib.ioc.test.v12.controller.HelloControllerV12
import top.wcpe.taboolib.ioc.test.v12.service.GreetingServiceV12

@Service
class ShowcaseV12 @Inject constructor(
    private val greeting: GreetingServiceV12,
    private val controller: HelloControllerV12,
) {

    fun runAll(): List<String> {
        return listOf(
            "greet=${greeting.greet("v12")}",
            "controller=${controller.handle("v12")}",
        )
    }
}

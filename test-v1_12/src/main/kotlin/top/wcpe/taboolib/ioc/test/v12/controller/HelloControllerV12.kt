package top.wcpe.taboolib.ioc.test.v12.controller

import top.wcpe.taboolib.ioc.annotation.Controller
import top.wcpe.taboolib.ioc.annotation.Inject
import top.wcpe.taboolib.ioc.test.v12.service.GreetingServiceV12

@Controller
class HelloControllerV12 {

    @Inject
    lateinit var greeting: GreetingServiceV12

    fun handle(name: String): String = "[v12] ${greeting.greet(name)}"
}

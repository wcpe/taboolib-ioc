package top.wcpe.taboolib.ioc.test.v20.controller

import top.wcpe.taboolib.ioc.annotation.Controller
import top.wcpe.taboolib.ioc.annotation.Inject
import top.wcpe.taboolib.ioc.test.v20.service.GreetingService20

@Controller
class HelloControllerV20 {

    @Inject
    lateinit var greetingService: GreetingService20

    fun hello(name: String): String = greetingService.greet(name)
}

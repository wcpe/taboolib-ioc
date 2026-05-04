package top.wcpe.taboolib.ioc.test.v20.service

import top.wcpe.taboolib.ioc.annotation.Service

@Service
class GreetingService20 {
    fun greet(name: String): String = "hello, $name"
}

package top.wcpe.taboolib.ioc.test.v20.command

import taboolib.common.platform.ProxyCommandSender
import taboolib.common.platform.command.CommandBody
import taboolib.common.platform.command.CommandHeader
import taboolib.common.platform.command.subCommand
import top.wcpe.taboolib.ioc.annotation.Inject
import top.wcpe.taboolib.ioc.test.v20.controller.HelloControllerV20

@CommandHeader(name = "hello-ioc-v20")
object HelloIocCommandV20 {

    @Inject
    lateinit var controller: HelloControllerV20

    @CommandBody
    val main = subCommand {
        execute<ProxyCommandSender> { sender, _, _ ->
            sender.sendMessage(controller.hello("world"))
        }
    }
}

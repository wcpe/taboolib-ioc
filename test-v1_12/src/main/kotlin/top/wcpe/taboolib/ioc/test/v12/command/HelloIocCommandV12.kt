package top.wcpe.taboolib.ioc.test.v12.command

import taboolib.common.platform.ProxyCommandSender
import taboolib.common.platform.command.CommandBody
import taboolib.common.platform.command.CommandHeader
import taboolib.common.platform.command.subCommand
import top.wcpe.taboolib.ioc.annotation.Inject
import top.wcpe.taboolib.ioc.test.v12.ShowcaseV12

@CommandHeader(name = "hello-ioc-v12")
object HelloIocCommandV12 {

    @Inject
    lateinit var showcase: ShowcaseV12

    @CommandBody
    val main = subCommand {
        execute<ProxyCommandSender> { sender, _, _ ->
            showcase.runAll().forEach(sender::sendMessage)
        }
    }
}

package top.wcpe.ioc.example.command

import taboolib.common.platform.ProxyCommandSender
import taboolib.common.platform.command.CommandBody
import taboolib.common.platform.command.CommandHeader
import taboolib.common.platform.command.subCommand
import top.wcpe.ioc.example.ExamplePlugin
import top.wcpe.ioc.example.controller.ExampleFeatureController
import top.wcpe.taboolib.ioc.annotation.Inject

@CommandHeader(
    name = "taboolibioc",
    aliases = ["ioc"],
    permission = "taboolibioc.admin",
    permissionDefault = taboolib.common.platform.command.PermissionDefault.OP
)
object ExampleCommand {
    @Inject
    lateinit var featureController: ExampleFeatureController

    @CommandBody
    val test = subCommand {
        execute<ProxyCommandSender> { sender, _, _ ->
            ExamplePlugin.featureController.runAllChecks().forEach(sender::sendMessage)
        }
    }
}
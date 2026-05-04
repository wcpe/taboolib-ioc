package top.wcpe.taboolib.ioc.test.v20

import top.wcpe.taboolib.ioc.annotation.Inject
import top.wcpe.taboolib.ioc.annotation.Service
import top.wcpe.taboolib.ioc.test.v20.aspect.LogAspectV20
import top.wcpe.taboolib.ioc.test.v20.config.AppInfo20
import top.wcpe.taboolib.ioc.test.v20.config.Banner20
import top.wcpe.taboolib.ioc.test.v20.config.GreetingHolder20
import top.wcpe.taboolib.ioc.test.v20.controller.HelloControllerV20
import top.wcpe.taboolib.ioc.test.v20.repository.UserRepoV20
import top.wcpe.taboolib.ioc.test.v20.service.GreetingService20

@Service
class Showcase20 {

    @Inject
    lateinit var greetingService: GreetingService20

    @Inject
    lateinit var userRepo: UserRepoV20

    @Inject
    lateinit var helloController: HelloControllerV20

    @Inject
    lateinit var appInfo: AppInfo20

    @Inject
    lateinit var greetingHolder: GreetingHolder20

    @Inject
    lateinit var primaryBanner: Banner20

    fun runAll(): List<String> {
        userRepo.save("u1", "Alice")
        userRepo.save("u2", "Bob")
        return listOf(
            "service: ${greetingService.greet("world")}",
            "controller: ${helloController.hello("ioc")}",
            "users: ${userRepo.findAll()}",
            "appInfo: ${appInfo.name}-${appInfo.version}",
            "holder: ${greetingHolder.render("holder")}",
            "banner: ${primaryBanner.text()}",
            "aspect-calls: ${LogAspectV20.callCount.get()}"
        )
    }
}

package top.wcpe.taboolib.ioc.test.v12.service

import top.wcpe.taboolib.ioc.annotation.Inject
import top.wcpe.taboolib.ioc.annotation.PostConstruct
import top.wcpe.taboolib.ioc.annotation.Service
import top.wcpe.taboolib.ioc.test.v12.repository.UserRepoV12

@Service
class GreetingServiceV12 @Inject constructor(
    private val repo: UserRepoV12,
) {

    private var initialized = false

    @PostConstruct
    fun init() {
        initialized = true
    }

    fun greet(name: String): String {
        check(initialized)
        return "hello-${repo.tag()}-$name"
    }
}

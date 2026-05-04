package top.wcpe.taboolib.ioc.test.v20.config

import top.wcpe.taboolib.ioc.annotation.Bean
import top.wcpe.taboolib.ioc.annotation.Configuration
import top.wcpe.taboolib.ioc.annotation.Primary
import top.wcpe.taboolib.ioc.test.v20.service.GreetingService20

data class AppInfo20(val name: String, val version: String)

interface Banner20 {
    fun text(): String
}

class DefaultBanner20 : Banner20 {
    override fun text(): String = "default-banner"
}

class PrimaryBanner20 : Banner20 {
    override fun text(): String = "primary-banner"
}

class GreetingHolder20(val service: GreetingService20) {
    fun render(name: String): String = "[banner] ${service.greet(name)}"
}

@Configuration
class AppConfigV20 {

    @Bean
    fun appInfo(): AppInfo20 = AppInfo20("test-v20", "1.0.0")

    @Bean
    fun greetingHolder(service: GreetingService20): GreetingHolder20 = GreetingHolder20(service)

    @Primary
    @Bean
    fun primaryBanner(): Banner20 = PrimaryBanner20()

    @Bean("defaultBanner")
    fun defaultBanner(): Banner20 = DefaultBanner20()
}

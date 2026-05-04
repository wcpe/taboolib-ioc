package top.wcpe.taboolib.ioc.test.v12.config

import top.wcpe.taboolib.ioc.annotation.Bean
import top.wcpe.taboolib.ioc.annotation.Configuration
import top.wcpe.taboolib.ioc.annotation.Primary

interface MathV12 {
    fun add(a: Int, b: Int): Int
}

class SimpleMathV12 : MathV12 {
    override fun add(a: Int, b: Int): Int = a + b
}

class DoubleMathV12 : MathV12 {
    override fun add(a: Int, b: Int): Int = (a + b) * 2
}

@Configuration
class AppConfigV12 {

    @Primary
    @Bean
    fun simpleMath(): MathV12 = SimpleMathV12()

    @Bean("doubleMath")
    fun doubleMath(): MathV12 = DoubleMathV12()

    @Bean
    fun appName(): String = "TestV12"
}

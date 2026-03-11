import io.izzel.taboolib.gradle.Basic
import io.izzel.taboolib.gradle.Bukkit

plugins {
    id("io.izzel.taboolib")
    kotlin("jvm")
}


taboolib {
    description {
        name("TaboolibIoCExamplePlugin")
        desc("Taboolib IoC 示例插件")
        contributors {
            name("WCPE")
        }
        dependencies {
        }
    }
    env {
        debug = true
        install(Basic)
        install(Bukkit)
        group = "top.wcpe.ioc.example"
    }
    relocate("top.wcpe.taboolib.ioc", "top.wcpe.ioc.example.ioc")
    subproject = false
}

dependencies {
    taboo(project(":taboolib-ioc"))
}

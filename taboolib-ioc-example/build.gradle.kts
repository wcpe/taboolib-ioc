import io.izzel.taboolib.gradle.*

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
        install(Basic)
        install(Bukkit)
    }
    subproject = false
}

dependencies {
    taboo(project(":taboolib-ioc"))
}

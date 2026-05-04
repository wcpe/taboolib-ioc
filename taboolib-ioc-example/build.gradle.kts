import io.izzel.taboolib.gradle.Basic
import io.izzel.taboolib.gradle.Bukkit

plugins {
    id("io.izzel.taboolib")
    id("xyz.jpenilla.run-paper")
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

    // 测试依赖 - 直接引用 ioc-core 和 ioc-api 以访问容器内部 API
    testImplementation(project(":taboolib-ioc-core"))
    testImplementation(project(":taboolib-ioc-api"))
    testImplementation(project(":taboolib-ioc-annotation"))
    testImplementation(project(":taboolib-ioc-test"))
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.8.1")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.8.1")
}

tasks.withType<Test> {
    workingDir = layout.buildDirectory.dir("taboolib-ioc/run").get().asFile.also { it.mkdirs() }
    useJUnitPlatform()
}

// 示例模块不需要发布
tasks.matching { it.name.startsWith("publish") }.configureEach {
    enabled = false
}

tasks.named<xyz.jpenilla.runpaper.task.RunServer>("runServer") {
    minecraftVersion("1.20.4")
    pluginJars(tasks.named("taboolibBuildPlugin").map { it.outputs.files.singleFile })
}

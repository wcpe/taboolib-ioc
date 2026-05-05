import io.izzel.taboolib.gradle.Basic
import io.izzel.taboolib.gradle.Bukkit

plugins {
    id("io.izzel.taboolib")
    id("xyz.jpenilla.run-paper")
    id("dev.s7a.gradle.minecraft.server")
    kotlin("jvm")
}

taboolib {
    description {
        name("TaboolibIoCTestV12")
        desc("Taboolib IoC 示例插件 (1.12.2)")
        contributors {
            name("WCPE")
        }
        dependencies {
        }
    }
    env {
        install(Basic)
        install(Bukkit)
        group = "top.wcpe.ioc.testv12"
    }
    relocate("top.wcpe.taboolib.ioc", "top.wcpe.ioc.testv12.ioc")
    subproject = false
}

repositories {
    mavenCentral()
    maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
}

dependencies {
    taboo(project(":taboolib-ioc"))

    compileOnly(kotlin("stdlib"))
    // MockBukkit-v1.13 对应的 Bukkit/Spigot API 已由 MockBukkit 依赖传递提供；
    // 此处无需显式声明 spigot-api（1.12.2 的 spigot-api 仓库常不可达）。

    // IoC 测试工具依赖
    testImplementation(project(":taboolib-ioc-core"))
    testImplementation(project(":taboolib-ioc-api"))
    testImplementation(project(":taboolib-ioc-annotation"))
    testImplementation(project(":taboolib-ioc-test"))

    // MockBukkit for 1.12 (artifact name is MockBukkit-v1.13)
    testImplementation("com.github.seeseemelk:MockBukkit-v1.13:0.2.0")

    // JUnit 5
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.8.1")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.8.1")
}

tasks.withType<Test> {
    workingDir = layout.buildDirectory.dir("taboolib-ioc/run").get().asFile.also { it.mkdirs() }
    useJUnitPlatform()
}

// 测试模块不需要发布
tasks.matching { it.name.startsWith("publish") }.configureEach {
    enabled = false
}

tasks.named<xyz.jpenilla.runpaper.task.RunServer>("runServer") {
    minecraftVersion("1.12.2")
    pluginJars(tasks.named("taboolibMainTask").map { it.outputs.files.singleFile })
}

// Paper 没有 1.12.2 官方构建，使用 dev.s7a.gradle.minecraft.server 通过 BuildTools 风格的
// Spigot 构建启动 1.12.2 真实服务器。任务名 runSpigot12，会自动下载 spigot 1.12.2 构建产物。
tasks.register<dev.s7a.gradle.minecraft.server.tasks.LaunchMinecraftServerTask>("runSpigot12") {
    group = "run paper"
    description = "Run a real Spigot 1.12.2 server with the built plugin jar."
    dependsOn("taboolibMainTask")
    doFirst {
        val pluginsDir = layout.buildDirectory.dir("MinecraftServer12/plugins").get().asFile
        pluginsDir.mkdirs()
        copy {
            from(tasks.named("taboolibMainTask").get().outputs.files)
            into(pluginsDir)
        }
    }
    serverDirectory.set(layout.buildDirectory.dir("MinecraftServer12").get().asFile.absolutePath)
    // getbukkit.org 镜像保留 1.12.2 spigot 构建
    jarUrl.set(object : dev.s7a.gradle.minecraft.server.tasks.LaunchMinecraftServerTask.JarUrl {
        override fun get(): String = "https://download.getbukkit.org/spigot/spigot-1.12.2.jar"
    })
    jarName.set("spigot-1.12.2.jar")
    agreeEula.set(true)
}

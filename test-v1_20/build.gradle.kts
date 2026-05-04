import io.izzel.taboolib.gradle.Basic
import io.izzel.taboolib.gradle.Bukkit

plugins {
    id("io.izzel.taboolib")
    id("xyz.jpenilla.run-paper")
    kotlin("jvm")
}

taboolib {
    description {
        name("TaboolibIocTestV20")
        desc("Taboolib IoC test-v1_20 演示插件")
        contributors {
            name("WCPE")
        }
    }
    env {
        install(Basic)
        install(Bukkit)
        group = "top.wcpe.ioc.testv20"
    }
    relocate("top.wcpe.taboolib.ioc", "top.wcpe.ioc.testv20.ioc")
    subproject = false
}

// MockBukkit-v1.20:3.93.2 需要 Java 21
tasks.withType<JavaCompile> {
    sourceCompatibility = "21"
    targetCompatibility = "21"
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions {
        jvmTarget = "21"
        freeCompilerArgs = listOf("-Xjvm-default=all")
    }
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    // 作为插件 jar 构建的运行时依赖
    taboo(project(":taboolib-ioc"))

    // main 源集使用 stdlib + paper-api
    compileOnly(kotlin("stdlib"))
    compileOnly("io.papermc.paper:paper-api:1.20.4-R0.1-SNAPSHOT")

    // 测试模块依赖 IoC 模块
    testImplementation(project(":taboolib-ioc-core"))
    testImplementation(project(":taboolib-ioc-api"))
    testImplementation(project(":taboolib-ioc-annotation"))
    testImplementation(project(":taboolib-ioc-test"))

    // MockBukkit + paper-api
    testImplementation("com.github.seeseemelk:MockBukkit-v1.20:3.93.2")
    testImplementation("io.papermc.paper:paper-api:1.20.4-R0.1-SNAPSHOT")

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
    minecraftVersion("1.20.4")
    pluginJars(tasks.named("taboolibBuildPlugin").map { it.outputs.files.singleFile })
}

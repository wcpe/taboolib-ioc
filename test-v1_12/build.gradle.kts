import io.izzel.taboolib.gradle.Basic
import io.izzel.taboolib.gradle.Bukkit

plugins {
    id("io.izzel.taboolib")
    id("top.wcpe.mc-testkit")
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

    // 判定桩说明：本模块 target 为 Java 8，而 harness-core:0.1.0 仅兼容 JVM 17+，
    // 故此处不引入 harness-core；判定桩 McTestkitVerdict 自写结果文件写出逻辑（仅 Java 8 API）。
}

tasks.withType<Test> {
    workingDir = layout.buildDirectory.dir("taboolib-ioc/run").get().asFile.also { it.mkdirs() }
    useJUnitPlatform()
}

// 测试模块不需要发布
tasks.matching { it.name.startsWith("publish") }.configureEach {
    enabled = false
}

// ---------------------------------------------------------------------------
// mc-testkit 起服验证（取代旧的行内 Paper 起服任务与 Spigot 手写起服任务）
// 判定真源 = 结果文件 build/mc-testkit/results/smoke.properties 的 status=PASS。
// 1.12.2 的 Spigot 服务端由 mc-testkit 内置下载承担（主源 getbukkit 已失效时自动回退
// GitHub 镜像，可经 MC_TESTKIT_E2E_SPIGOT_JAR 预置覆盖）。
// 自测模式（mc-testkit 0.9.0+）：未声明 pluginUnderTest 时框架自动取本模块 jar 产物
// （build/libs/<name>-<version>.jar），并把 prepareE2eSmoke / e2eSmoke 自动依赖到 jar 任务；
// 运行期仍可用 MC_TESTKIT_E2E_PLUGIN_UNDER_TEST_JAR 覆盖（CI / GradleRunner 注入）。
// ---------------------------------------------------------------------------
mcTestkit {
    backend("s1") {
        platform = spigot
        version = "1.12.2"
        // 指定非默认端口（默认 25565 易被本机其它服务占用，导致 BindException）；按模块错开避免并发撞端口
        port = 25567
    }
    scenario("smoke")
}

import io.izzel.taboolib.gradle.Basic
import io.izzel.taboolib.gradle.Bukkit

plugins {
    id("io.izzel.taboolib")
    id("top.wcpe.mc-testkit")
    kotlin("jvm")
    id("top.wcpe.taboolib.ioc")
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
    subproject = false
}

dependencies {
    // 测试依赖 - 直接引用 ioc-core 和 ioc-api 以访问容器内部 API
    testImplementation(project(":taboolib-ioc-core"))
    testImplementation(project(":taboolib-ioc-api"))
    testImplementation(project(":taboolib-ioc-annotation"))
    testImplementation(project(":taboolib-ioc-test"))
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.8.1")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.8.1")

    // 判定桩说明：本模块 target 为 Java 8，而 harness-core:0.1.0 仅兼容 JVM 17+，
    // 故此处不引入 harness-core；判定桩 McTestkitVerdict 自写结果文件写出逻辑（仅 Java 8 API）。
}

tasks.withType<Test> {
    workingDir = layout.buildDirectory.dir("taboolib-ioc/run").get().asFile.also { it.mkdirs() }
    useJUnitPlatform()
}

// 示例模块不需要发布
tasks.matching { it.name.startsWith("publish") }.configureEach {
    enabled = false
}

// ---------------------------------------------------------------------------
// mc-testkit 起服验证（取代旧的行内 Paper 起服任务）
// 后端版本取 1.20.1（mc-testkit 代表版本，不含原 1.20.4）。
// 自测模式（mc-testkit 0.9.0+）：未声明 pluginUnderTest 时框架自动取本模块 jar 产物
// （build/libs/<name>-<version>.jar），并把 prepareE2eSmoke / e2eSmoke 自动依赖到 jar 任务；
// 运行期仍可用 MC_TESTKIT_E2E_PLUGIN_UNDER_TEST_JAR 覆盖（CI / GradleRunner 注入）。
// ---------------------------------------------------------------------------
mcTestkit {
    backend("s1") {
        platform = paper
        version = "1.20.1"
        // 指定非默认端口（默认 25565 易被本机其它服务占用，导致 BindException）；按模块错开避免并发撞端口
        port = 25568
    }
    scenario("smoke")
}

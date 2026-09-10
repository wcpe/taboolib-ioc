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
// 1.12.2 的 Spigot 服务端由 mc-testkit 内置下载承担（下载源 getbukkit，可经
// MC_TESTKIT_E2E_SPIGOT_JAR 预置覆盖）。
// ---------------------------------------------------------------------------
//
// pluginUnderTest 取值采用「双轨」：
//   - 优先读环境变量 MC_TESTKIT_E2E_PLUGIN_UNDER_TEST_JAR（CI / 外部覆盖，绝对路径）；
//   - 缺省回退到本模块 taboolibMainTask 产物的绝对路径（本地直接 `gradlew :test-v1_12:e2eSmoke` 即可跑）。
// 实测确认产物规则 = <module>/build/libs/<project.name>-<project.version>.jar
//   （本项目实测为 test-v1_12/build/libs/test-v1_12-1.2.0.jar）。
// pluginUnderTest 的解析发生在 prepare 任务的 doLast（执行期），配置期求值绝对路径不会触发早期校验失败。
val pluginUnderTestJarPath: String = layout.buildDirectory
    .file("libs/${project.name}-${project.version}.jar")
    .get().asFile.absolutePath

mcTestkit {
    backend("s1") {
        platform = spigot
        version = "1.12.2"
        // 指定非默认端口（默认 25565 易被本机其它服务占用，导致 BindException）；按模块错开避免并发撞端口
        port = 25567
    }
    scenario("smoke") // 无 bot：仅 prepare + verify（插件桩写结果文件即 PASS）
    dependencies {
        pluginUnderTest = System.getenv("MC_TESTKIT_E2E_PLUGIN_UNDER_TEST_JAR")
            ?.takeIf { it.isNotBlank() } ?: pluginUnderTestJarPath
    }
}

// 保证先产出被注入的插件 jar。
// 注意：mc-testkit 的任务在 afterEvaluate 中注册，故此处不能用 tasks.named("e2eSmoke")（配置期尚不存在），
// 改用 tasks.matching{}.configureEach{} 惰性挂钩，待任务注册/实现时再追加依赖。
tasks.matching { it.name == "e2eSmoke" }.configureEach {
    dependsOn("taboolibMainTask")
}

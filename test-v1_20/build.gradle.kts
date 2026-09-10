import io.izzel.taboolib.gradle.Basic
import io.izzel.taboolib.gradle.Bukkit

plugins {
    id("io.izzel.taboolib")
    id("top.wcpe.mc-testkit")
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

    // mc-testkit 判定桩所需的 harness 工具类（McTestkitEnv / McTestkitResultWriter，写结果文件用）。
    // 该桩在插件 jar 内运行，必须作为运行时依赖随插件打进 jar。
    // 用 TabooLib 的 `taboo` 配置（其内容会被 TabooLib 合并进插件 jar，并参与 relocate）。
    taboo("top.wcpe.mc:harness-core:0.1.0")
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
// mc-testkit 起服验证（取代旧的行内 Paper 起服任务）
// 判定真源 = 结果文件 build/mc-testkit/results/smoke.properties 的 status=PASS。
// 后端版本取 1.20.1（mc-testkit 代表版本含 1.20.1，不含原 1.20.4）。
// ---------------------------------------------------------------------------
//
// pluginUnderTest 取值采用「双轨」：
//   - 优先读环境变量 MC_TESTKIT_E2E_PLUGIN_UNDER_TEST_JAR（CI / 外部覆盖，绝对路径）；
//   - 缺省回退到本模块 taboolibMainTask 产物的绝对路径（本地直接 `gradlew :test-v1_20:e2eSmoke` 即可跑）。
// 实测确认产物规则 = <module>/build/libs/<project.name>-<project.version>.jar
//   （本项目实测为 test-v1_20/build/libs/test-v1_20-1.2.0.jar）。
// pluginUnderTest 的解析发生在 prepare 任务的 doLast（执行期），配置期求值绝对路径不会触发早期校验失败。
val pluginUnderTestJarPath: String = layout.buildDirectory
    .file("libs/${project.name}-${project.version}.jar")
    .get().asFile.absolutePath

mcTestkit {
    backend("s1") {
        platform = paper
        version = "1.20.1"
        // 指定非默认端口（默认 25565 易被本机其它服务占用，导致 BindException）；按模块错开避免并发撞端口
        port = 25566
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

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
// pluginUnderTest 采用「双轨」：env 覆盖优先，缺省回退本模块产物绝对路径。
// ---------------------------------------------------------------------------
val pluginUnderTestJarPath: String = layout.buildDirectory
    .file("libs/${project.name}-${project.version}.jar")
    .get().asFile.absolutePath

mcTestkit {
    backend("s1") {
        platform = paper
        version = "1.20.1"
        // 指定非默认端口（默认 25565 易被本机其它服务占用，导致 BindException）；按模块错开避免并发撞端口
        port = 25568
    }
    scenario("smoke")
    dependencies {
        pluginUnderTest = System.getenv("MC_TESTKIT_E2E_PLUGIN_UNDER_TEST_JAR")
            ?.takeIf { it.isNotBlank() } ?: pluginUnderTestJarPath
    }
}

// 保证先产出被注入的插件 jar。
// 注意：mc-testkit 的任务在 afterEvaluate 中注册，故此处不能用 tasks.named("e2eSmoke")（配置期尚不存在），
// 改用 tasks.matching{}.configureEach{} 惰性挂钩，待任务注册/实现时再追加依赖。
// prepareE2eSmoke 会做前置校验（被测插件 jar 必须已存在），故它与 e2eSmoke 都必须依赖
// 产出插件 jar 的 `jar` 任务。**不能只依赖 taboolibMainTask**——它只是 `jar` 的 finalizer，
// 单独调度时不会带上 `jar`，反而会因 inJar 不存在而失败（CI 全新检出时即暴露此问题）。
tasks.matching { it.name == "e2eSmoke" || it.name == "prepareE2eSmoke" }.configureEach {
    dependsOn("jar")
}

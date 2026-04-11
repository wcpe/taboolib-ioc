plugins {
    id("io.izzel.taboolib")
    kotlin("jvm")
}

taboolib {
    subproject = true
}

repositories {
    mavenCentral()
}

dependencies {
    // IoC 模块依赖
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

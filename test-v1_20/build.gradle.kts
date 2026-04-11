plugins {
    id("io.izzel.taboolib")
    kotlin("jvm")
}

taboolib {
    subproject = true
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
}

dependencies {
    // IoC 模块依赖
    testImplementation(project(":taboolib-ioc-core"))
    testImplementation(project(":taboolib-ioc-api"))
    testImplementation(project(":taboolib-ioc-annotation"))
    testImplementation(project(":taboolib-ioc-test"))

    // MockBukkit
    testImplementation("com.github.seeseemelk:MockBukkit-v1.20:3.93.2")

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

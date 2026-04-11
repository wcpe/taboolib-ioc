import io.izzel.taboolib.gradle.*

plugins {
    id("io.izzel.taboolib")
    kotlin("jvm")
}

taboolib {
    subproject = true
}

dependencies {
    api(project(":taboolib-ioc-api"))
    compileOnly("org.tabooproject.reflex:reflex:1.2.2")
    compileOnly("org.tabooproject.reflex:analyser:1.2.2")

    // 测试依赖
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.8.1")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.8.1")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

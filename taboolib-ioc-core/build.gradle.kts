import io.izzel.taboolib.gradle.*

plugins {
    id("io.izzel.taboolib")
    kotlin("jvm")
    `java-test-fixtures`
}

taboolib {
    subproject = true
}

dependencies {
    api(project(":taboolib-ioc-api"))
    compileOnly("org.tabooproject.reflex:reflex:1.2.2")
    compileOnly("org.tabooproject.reflex:analyser:1.2.2")

    // testFixtures 依赖（IocTestContext 需要访问 core 的内部类）
    testFixturesImplementation(project(":taboolib-ioc-api"))
    testFixturesImplementation(project(":taboolib-ioc-annotation"))

    // 测试依赖
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.8.1")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.8.1")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

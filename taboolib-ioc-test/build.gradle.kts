import io.izzel.taboolib.gradle.*

plugins {
    id("io.izzel.taboolib")
    `maven-publish`
}

taboolib {
    subproject = true
}

dependencies {
    api(project(":taboolib-ioc"))
    api(project(":taboolib-ioc-annotation"))
    api("io.izzel.taboolib:common")
    api("org.junit.jupiter:junit-jupiter-api:5.8.1")
    runtimeOnly("org.junit.jupiter:junit-jupiter-engine:5.8.1")
}
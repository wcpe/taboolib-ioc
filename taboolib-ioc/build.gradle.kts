import io.izzel.taboolib.gradle.*

plugins {
    id("io.izzel.taboolib")
    `maven-publish`
}

taboolib {
    subproject = true
}

dependencies {
    api(project(":taboolib-ioc-api"))
    api(project(":taboolib-ioc-core"))
}

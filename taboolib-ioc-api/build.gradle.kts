plugins {
    kotlin("jvm")
}

taboolib {
    subproject = true
}

dependencies {
    api(project(":taboolib-ioc-annotation"))
}

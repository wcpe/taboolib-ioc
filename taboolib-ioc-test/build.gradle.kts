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
    // 必须带版本号：无版本的坐标无法解析（io.izzel.taboolib:common:.），
    // 与 taboolib { version { taboolib = "6.2.4-fa94b997" } } 保持一致
    api("io.izzel.taboolib:common:6.2.4-fa94b997")
    api("org.junit.jupiter:junit-jupiter-api:5.8.1")
    runtimeOnly("org.junit.jupiter:junit-jupiter-engine:5.8.1")
}
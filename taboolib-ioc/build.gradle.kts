import io.izzel.taboolib.gradle.*

plugins {
    id("io.izzel.taboolib")
    `maven-publish`
}

// 聚合模块不需要打包 Taboolib 运行时
taboolib {
    subproject = false
}

dependencies {
    api(project(":taboolib-ioc-api"))
    api(project(":taboolib-ioc-core"))
}

publishing {
    repositories {
        maven {
            credentials {
                username = project.findProperty("username")?.toString() ?: ""
                password = project.findProperty("password")?.toString() ?: ""
            }
            authentication {
                create<BasicAuthentication>("basic")
            }
            val releasesRepoUrl = uri("https://maven.wcpe.top/repository/maven-releases/")
            val snapshotsRepoUrl = uri("https://maven.wcpe.top/repository/maven-snapshots/")
            url = if (version.toString().endsWith("SNAPSHOT")) snapshotsRepoUrl else releasesRepoUrl
        }
    }
    publications {
        create<MavenPublication>("maven") {
            groupId = "top.wcpe.taboolib.ioc"
            artifactId = "taboolib-ioc"
            version = "1.0.0-SNAPSHOT"
            from(components["java"])
        }
    }
}

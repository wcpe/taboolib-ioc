import io.izzel.taboolib.gradle.*

plugins {
    java
    `maven-publish`
    id("io.izzel.taboolib") version "2.0.38-wcpe.1" apply false
    id("top.wcpe.mc-testkit") version "0.9.0" apply false
    id("top.wcpe.taboolib.ioc") version "0.0.8" apply false
    kotlin("jvm") version "1.9.25" apply false
}

subprojects {
    apply(plugin = "java")
    apply(plugin = "io.izzel.taboolib")
    apply(plugin = "maven-publish")
    apply(plugin = "org.jetbrains.kotlin.jvm")

    configure<TabooLibExtension> {
        env {
            install(Basic)
        }
        version { taboolib = "6.2.4-fa94b997" }
    }

    repositories {
        // 顺序敏感：可达全量镜像前置，其次 wcpe 私有 releases（2.0.38-wcpe.1 与
        // mc-testkit 0.8.0 只在 releases，public 未聚合），最后 mavenLocal / central 兜底。
        maven("https://maven.aliyun.com/repository/public")
        maven("https://maven.wcpe.top/repository/maven-releases/")
        maven("https://maven.wcpe.top/repository/maven-public/")
        mavenLocal()
        mavenCentral()
        maven("https://repo.tabooproject.org/repository/releases")
    }

    dependencies {
        "compileOnly"(kotlin("stdlib"))
    }

    tasks.withType<JavaCompile> {
        options.encoding = "UTF-8"
        sourceCompatibility = "1.8"
        targetCompatibility = "1.8"
    }
    java {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
        withSourcesJar()
    }

    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        kotlinOptions {
            jvmTarget = "1.8"
            freeCompilerArgs = listOf("-Xjvm-default=all")
        }
    }


    publishing {
        repositories {
            maven {
                credentials {
                    // 优先读取 WCPE_MAVEN_USERNAME/PASSWORD；回退旧名 username/password 保持向后兼容。
                    // 旧名那对值对 maven-releases 的 PUT 返回 401，只有 WCPE_MAVEN_* 返回 201。
                    username = (project.findProperty("WCPE_MAVEN_USERNAME")
                        ?: project.findProperty("username"))?.toString() ?: ""
                    password = (project.findProperty("WCPE_MAVEN_PASSWORD")
                        ?: project.findProperty("password"))?.toString() ?: ""
                }
                authentication {
                    create<BasicAuthentication>("basic")
                }
                val releasesRepoUrl = uri("https://maven.wcpe.top/repository/maven-releases/")
                val snapshotsRepoUrl = uri("https://maven.wcpe.top/repository/maven-snapshots/")
                url = if (version.toString().endsWith("SNAPSHOT")) snapshotsRepoUrl else releasesRepoUrl
            }
            mavenLocal()
        }
        publications {
            create<MavenPublication>("maven") {
                groupId = "${rootProject.group}"
                artifactId = project.name
                version = "${project.version}"
                from(components["java"])
                println("> Apply \"$groupId:$artifactId:$version\"")
            }
        }
    }

}

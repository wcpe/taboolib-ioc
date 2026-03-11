import io.izzel.taboolib.gradle.*

plugins {
    java
    `maven-publish`
    id("io.izzel.taboolib") version "2.0.28" apply false
    kotlin("jvm") version "2.1.0" apply false
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
        mavenLocal()
        maven("https://maven.wcpe.top/repository/maven-public/")
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

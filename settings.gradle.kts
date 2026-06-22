rootProject.name = "taboolib-ioc"

pluginManagement {
    repositories {
        maven("https://maven.wcpe.top/repository/maven-public/")

        mavenLocal()
        mavenCentral()
        gradlePluginPortal()
    }
}


include("taboolib-ioc-annotation")
include("taboolib-ioc-api")
include("taboolib-ioc-core")
include("taboolib-ioc")
include("taboolib-ioc-test")
include("taboolib-ioc-example")
include("test-v1_20")
include("test-v1_12")

rootProject.name = "taboolib-ioc"

pluginManagement {
    repositories {
        // 顺序敏感：阿里云 public 是 Central 全量镜像且经本机代理可达，优先解析以加速；
        // wcpe 兜底私有产物，mavenLocal 供本地联调，mavenCentral / gradlePluginPortal
        // 供 CI / 无代理环境兜底。
        mavenLocal()
        maven("https://maven.aliyun.com/repository/public")
        maven("https://maven.aliyun.com/repository/gradle-plugin")
        maven("https://maven.wcpe.top/repository/maven-public/")
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

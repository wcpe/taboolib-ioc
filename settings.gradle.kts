rootProject.name = "taboolib-ioc"

pluginManagement {
    repositories {
        // 顺序敏感：阿里云 public 是 Central 全量镜像且经本机代理可达，优先解析以加速；
        // wcpe releases 承载 2.0.38-wcpe.1 / mc-testkit 0.8.0（public 未聚合，实测 404），
        // 须前置于 mavenLocal；mavenLocal 供本地联调，central / gradlePluginPortal 兜底。
        maven("https://maven.aliyun.com/repository/public")
        maven("https://maven.aliyun.com/repository/gradle-plugin")
        maven("https://maven.wcpe.top/repository/maven-releases/")
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

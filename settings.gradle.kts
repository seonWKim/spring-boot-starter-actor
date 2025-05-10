rootProject.name = "spring-boot-starter-actor"

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

include("core")
include("core-boot3")
include("example:chat")
include("example:cluster")
include("example:simple")

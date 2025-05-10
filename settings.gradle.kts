rootProject.name = "spring-boot-starter-actor"

include("core")
include("example:chat")
include("example:cluster")
include("example:simple")

gradle.rootProject {
    extra["projectVersion"] = "0.0.4"
}

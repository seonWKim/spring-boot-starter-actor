rootProject.name = "spring-boot-starter-actor"

include(":actor-api")
include(":actor-core")
include(":actor-autoconfigure")
include(":actor-spring-boot-starter")
include(":actor-metrics")

// Relocation POMs: the 0.x coordinates point to the 1.0 artifacts for one release.
include(":relocation:spring-boot-starter-actor")
include(":relocation:spring-boot-starter-actor_3")
include(":relocation:spring-boot-starter-actor-metrics")

include(":example:agent")
include(":example:chat")
include(":example:metrics-example")
include(":example:cluster")
include(":example:kubernetes")
include(":example:logging")
include(":example:persistence")
include(":example:simple")
include(":example:synchronization")
include(":example:virtual-threads")

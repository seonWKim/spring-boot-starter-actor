rootProject.name = "actor-spring-boot-starter"

include(":actor-api")
include(":actor-core")
include(":actor-autoconfigure")
include(":actor-cluster")
include(":actor-spring-boot-starter")
include(":actor-spring-boot-starter-cluster")
include(":actor-metrics")
include(":actor-jdbc")
include(":actor-redis")
include(":actor-test")
include(":actor-streams")
include(":actor-persistence")

// Test-only: the ARCHITECTURE.md rules every module's ArchitectureTest runs. Not published.
include(":architecture-rules")

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

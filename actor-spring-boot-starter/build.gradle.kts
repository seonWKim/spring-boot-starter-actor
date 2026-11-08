// Empty starter: picks the modules an application needs.
val pekkoVersion: String by project

dependencies {
    api(project(":actor-api"))
    api(project(":actor-core"))
    api(project(":actor-autoconfigure"))

    // Temporary: public types in actor-core still have Pekko in their signatures, so
    // applications need Pekko to compile. This goes away when those APIs move to
    // actor-api without Pekko types (see "Migration status" in ARCHITECTURE.md).
    api("org.apache.pekko:pekko-actor-typed_3:$pekkoVersion")
    api("org.apache.pekko:pekko-cluster-typed_3:$pekkoVersion")
    api("org.apache.pekko:pekko-cluster-sharding-typed_3:$pekkoVersion")
    api("org.apache.pekko:pekko-serialization-jackson_3:$pekkoVersion")
}

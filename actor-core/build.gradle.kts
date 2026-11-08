plugins {
    id("io.spring.dependency-management")
    // Test actors shared with other modules' tests (actor-cluster runs them across nodes).
    `java-test-fixtures`
}

val pekkoVersion: String by project
val springBootVersion: String by project

dependencyManagement {
    imports {
        mavenBom("org.springframework.boot:spring-boot-dependencies:${springBootVersion}")
    }
}

dependencies {
    api(project(":actor-api"))

    // Pekko is the engine and an implementation detail (ARCHITECTURE.md).
    implementation("org.apache.pekko:pekko-actor-typed_3:$pekkoVersion")

    implementation("com.google.code.findbugs:jsr305")
    // SpringActorSystemBuilder and ActorSystemShutdownLifecycle have Spring types in their signatures.
    api("org.springframework:spring-context")
    implementation("org.springframework.boot:spring-boot-starter")

    testFixturesImplementation("org.apache.pekko:pekko-actor-typed_3:$pekkoVersion")
    testFixturesImplementation("com.fasterxml.jackson.core:jackson-annotations")
    testFixturesImplementation("com.google.code.findbugs:jsr305:3.0.2")

    testImplementation(project(":actor-autoconfigure"))
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.apache.pekko:pekko-actor-testkit-typed_3")
    testImplementation("org.awaitility:awaitility")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

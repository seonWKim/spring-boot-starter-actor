// Reactor Flux/Mono bridges to actors (ARCHITECTURE.md): Pekko Streams stays internal.
plugins {
    id("io.spring.dependency-management")
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
    api("io.projectreactor:reactor-core")

    // PekkoActorRef: the references the messages Flux hands to actors.
    implementation(project(":actor-core"))
    implementation("org.apache.pekko:pekko-actor-typed_3:$pekkoVersion")
    implementation("org.apache.pekko:pekko-stream-typed_3:$pekkoVersion")
    implementation("com.google.code.findbugs:jsr305")

    testImplementation(project(":actor-autoconfigure"))
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("io.projectreactor:reactor-test")
    testImplementation("org.awaitility:awaitility")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

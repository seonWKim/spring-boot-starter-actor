// The starter: picks the modules an application needs. Pekko is an implementation detail
// of actor-core and is not on the application's compile classpath.
plugins {
    id("io.spring.dependency-management")
}

val springBootVersion: String by project

dependencyManagement {
    imports {
        mavenBom("org.springframework.boot:spring-boot-dependencies:${springBootVersion}")
    }
}

dependencies {
    // Like every Spring Boot starter, it brings Spring Boot itself.
    api("org.springframework.boot:spring-boot-starter")
    api(project(":actor-api"))
    api(project(":actor-core"))
    api(project(":actor-autoconfigure"))
}

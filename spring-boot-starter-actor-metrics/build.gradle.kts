plugins {
    `java-library`
}

dependencies {
    // Agent API (compileOnly - provided via -javaagent at runtime)
    compileOnly(project(":metrics"))

    // Spring Boot auto-configuration (compileOnly - users provide their own version)
    compileOnly("org.springframework.boot:spring-boot-autoconfigure:2.7.0")

    // Micrometer (compileOnly - users provide this transitively via spring-boot-starter-actuator)
    compileOnly("io.micrometer:micrometer-core:1.11.0")

    // Logging
    compileOnly("org.slf4j:slf4j-api:2.0.17")
}

tasks.jar {
    archiveBaseName.set("spring-boot-starter-actor-metrics")
}

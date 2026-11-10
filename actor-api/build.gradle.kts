// The public API: what users code against. No Pekko dependency at all (ARCHITECTURE.md rule 1).
val springBootVersion: String by project

dependencies {
    api(platform("org.springframework.boot:spring-boot-dependencies:${springBootVersion}"))
    api("org.springframework:spring-context")
    // @EnableActorSupport imports the actor auto-configurations with @ImportAutoConfiguration.
    api("org.springframework.boot:spring-boot-autoconfigure")
    // SpringBehaviorContext.getLog() returns an SLF4J Logger (Spring Boot's logging API).
    api("org.slf4j:slf4j-api")

    testImplementation(platform("org.springframework.boot:spring-boot-dependencies:${springBootVersion}"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

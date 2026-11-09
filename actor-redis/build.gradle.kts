// SPI implementations on the application's RedisConnectionFactory (ARCHITECTURE.md): no Pekko.
// Versions come from the Spring Boot platform that actor-api exports.
dependencies {
    api(project(":actor-api"))
    api("org.springframework.data:spring-data-redis")

    testImplementation(testFixtures(project(":actor-api")))
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-starter-data-redis")
    testImplementation("org.testcontainers:junit-jupiter")
    // A cluster that forms through Redis.
    testImplementation(testFixtures(project(":actor-cluster")))
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}


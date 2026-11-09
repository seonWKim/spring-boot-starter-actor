// SPI implementations on the application's DataSource (ARCHITECTURE.md): plain JDBC, no Pekko.

dependencies {
    api(project(":actor-api"))

    testImplementation(testFixtures(project(":actor-api")))
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-starter-jdbc")
    testImplementation("com.h2database:h2")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:postgresql")
    testImplementation("org.testcontainers:mysql")
    testRuntimeOnly("org.postgresql:postgresql")
    testRuntimeOnly("com.mysql:mysql-connector-j")
    // A cluster that forms through the actor_peer table.
    testImplementation(testFixtures(project(":actor-cluster")))
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

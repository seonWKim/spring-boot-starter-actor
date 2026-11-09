// SPI implementations on the application's DataSource (ARCHITECTURE.md): plain JDBC, no Pekko.

dependencies {
    api(project(":actor-api"))

    testImplementation(project(":actor-test"))
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
    // Cluster Bootstrap for those nodes. Not in the fixtures' own dependencies: on actor-cluster's
    // test classpath it would start management (one fixed port) in every cluster test.
    testRuntimeOnly("org.apache.pekko:pekko-management-cluster-bootstrap_3:${property("pekkoManagementVersion")}")
    testRuntimeOnly("org.apache.pekko:pekko-management-cluster-http_3:${property("pekkoManagementVersion")}")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

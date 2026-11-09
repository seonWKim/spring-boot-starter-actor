plugins {
    id("io.spring.dependency-management")
}

val pekkoVersion: String by project
val pekkoProjectionVersion: String by project
val springBootVersion: String by project

dependencyManagement {
    imports {
        mavenBom("org.springframework.boot:spring-boot-dependencies:${springBootVersion}")
    }
}

dependencies {
    api(project(":actor-api"))
    // Reads the events actor-persistence stores, on the same DataSource.
    api(project(":actor-persistence"))
    implementation(project(":actor-core"))

    // Pekko is the engine and an implementation detail (ARCHITECTURE.md).
    implementation("org.apache.pekko:pekko-actor-typed_3:$pekkoVersion")
    implementation("org.apache.pekko:pekko-persistence-query_3:$pekkoVersion")
    implementation("org.apache.pekko:pekko-projection-core_3:$pekkoProjectionVersion")
    implementation("org.apache.pekko:pekko-projection-jdbc_3:$pekkoProjectionVersion")
    implementation("org.apache.pekko:pekko-projection-eventsourced_3:$pekkoProjectionVersion")
    implementation("org.apache.pekko:pekko-persistence-jdbc_3:${property("pekkoPersistenceJdbcVersion")}")

    implementation("com.google.code.findbugs:jsr305")
    implementation("org.springframework.boot:spring-boot-starter")
    // Handlers and offsets commit in one Spring transaction.
    implementation("org.springframework:spring-jdbc")
    implementation("org.springframework:spring-tx")

    testImplementation(project(":actor-autoconfigure"))
    // A projection in cluster mode runs as a cluster singleton.
    testImplementation(project(":actor-cluster"))
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-starter-jdbc")
    testImplementation("com.h2database:h2")
    // The offset table scripts and dialects on real databases (skipped without Docker).
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:postgresql")
    testImplementation("org.testcontainers:mysql")
    testRuntimeOnly("org.postgresql:postgresql")
    testRuntimeOnly("com.mysql:mysql-connector-j")
    testImplementation("org.awaitility:awaitility")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

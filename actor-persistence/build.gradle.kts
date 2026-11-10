plugins {
    id("io.spring.dependency-management")
}

val pekkoVersion: String by project
val pekkoPersistenceJdbcVersion: String by project
val springBootVersion: String by project

dependencyManagement {
    imports {
        mavenBom("org.springframework.boot:spring-boot-dependencies:${springBootVersion}")
    }
}

dependencies {
    api(project(":actor-api"))
    implementation(project(":actor-core"))

    // Pekko is the engine and an implementation detail (ARCHITECTURE.md).
    implementation("org.apache.pekko:pekko-actor-typed_3:$pekkoVersion")
    implementation("org.apache.pekko:pekko-persistence-typed_3:$pekkoVersion")
    implementation("org.apache.pekko:pekko-persistence-query_3:$pekkoVersion")
    implementation("org.apache.pekko:pekko-serialization-jackson_3:$pekkoVersion")
    // Journal and snapshots on the application's DataSource.
    implementation("org.apache.pekko:pekko-persistence-jdbc_3:$pekkoPersistenceJdbcVersion")

    implementation("com.google.code.findbugs:jsr305")
    implementation("org.springframework.boot:spring-boot-starter")
    implementation("org.springframework:spring-jdbc")
    // Sizes the JDBC threads to the pool when the DataSource is Hikari (Spring Boot's default).
    implementation("com.zaxxer:HikariCP")

    testImplementation(project(":actor-autoconfigure"))
    testImplementation(testFixtures(project(":actor-core")))
    // An event-sourced actor as a sharded entity, on a cluster of nodes in this JVM.
    testImplementation(project(":actor-cluster"))
    testImplementation(project(":actor-test"))
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-starter-jdbc")
    testImplementation("com.h2database:h2")
    // The schema scripts and the database type detection on real databases (skipped without Docker).
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:postgresql")
    testImplementation("org.testcontainers:mysql")
    testRuntimeOnly("org.postgresql:postgresql")
    testRuntimeOnly("com.mysql:mysql-connector-j")
    testImplementation("org.awaitility:awaitility")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

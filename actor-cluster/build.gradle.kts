plugins {
    id("io.spring.dependency-management")
}

val pekkoVersion: String by project
val pekkoManagementVersion: String by project
val springBootVersion: String by project

dependencyManagement {
    imports {
        mavenBom("org.springframework.boot:spring-boot-dependencies:${springBootVersion}")
    }
}

dependencies {
    api(project(":actor-api"))
    api(project(":actor-core"))

    implementation("org.apache.pekko:pekko-actor-typed_3:$pekkoVersion")
    implementation("org.apache.pekko:pekko-cluster-typed_3:$pekkoVersion")
    implementation("org.apache.pekko:pekko-cluster-sharding-typed_3:$pekkoVersion")

    // Optional: Cluster Bootstrap & Management HTTP
    // Additionally, add a discovery method dependency (e.g., pekko-discovery-kubernetes-api_3)
    compileOnly("org.apache.pekko:pekko-management-cluster-bootstrap_3:${pekkoManagementVersion}")
    compileOnly("org.apache.pekko:pekko-management-cluster-http_3:${pekkoManagementVersion}")

    implementation("com.google.code.findbugs:jsr305")
    implementation("org.springframework.boot:spring-boot-starter")

    testImplementation(project(":actor-autoconfigure"))
    testImplementation(testFixtures(project(":actor-core")))
    testImplementation("com.fasterxml.jackson.core:jackson-databind")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.apache.pekko:pekko-actor-testkit-typed_3:$pekkoVersion")
    testImplementation("org.awaitility:awaitility")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

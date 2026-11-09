plugins {
    id("io.spring.dependency-management")
    // DiscoveryClusterContract: the store modules' tests form clusters through their stores.
    `java-test-fixtures`
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
    // Leases (split brain resolver lease-majority, singleton and sharding use-lease).
    implementation("org.apache.pekko:pekko-coordination_3:$pekkoVersion")
    implementation("org.apache.pekko:pekko-cluster-sharding-typed_3:$pekkoVersion")
    implementation("org.apache.pekko:pekko-serialization-jackson_3:$pekkoVersion")

    // Optional: Cluster Bootstrap & Management HTTP
    // Additionally, add a discovery method dependency (e.g., pekko-discovery-kubernetes-api_3)
    compileOnly("org.apache.pekko:pekko-management-cluster-bootstrap_3:${pekkoManagementVersion}")
    compileOnly("org.apache.pekko:pekko-management-cluster-http_3:${pekkoManagementVersion}")

    implementation("com.google.code.findbugs:jsr305")
    implementation("org.springframework.boot:spring-boot-starter")

    testFixturesApi(project(":actor-autoconfigure"))
    testFixturesApi("org.springframework.boot:spring-boot-starter-test")
    testFixturesApi("org.apache.pekko:pekko-cluster-typed_3:$pekkoVersion")
    testFixturesApi("org.awaitility:awaitility")

    testImplementation(project(":actor-autoconfigure"))
    testImplementation(testFixtures(project(":actor-api")))
    testImplementation(testFixtures(project(":actor-core")))
    testImplementation("com.fasterxml.jackson.core:jackson-databind")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-starter-actuator")
    testImplementation(project(":actor-metrics"))
    testImplementation("org.apache.pekko:pekko-actor-testkit-typed_3:$pekkoVersion")
    testImplementation("org.awaitility:awaitility")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// The test fixtures are for this repository's tests only: keep them out of the published artifact.
val javaComponent = components["java"] as AdhocComponentWithVariants
javaComponent.withVariantsFromConfiguration(configurations["testFixturesApiElements"]) { skip() }
javaComponent.withVariantsFromConfiguration(configurations["testFixturesRuntimeElements"]) { skip() }
// The publish plugin adds the fixtures sources variant once the project is evaluated.
afterEvaluate {
    javaComponent.withVariantsFromConfiguration(configurations["testFixturesSourcesElements"]) { skip() }
}

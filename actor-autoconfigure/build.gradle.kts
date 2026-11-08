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
    implementation(project(":actor-core"))

    implementation("org.apache.pekko:pekko-actor-typed_3:$pekkoVersion")
    implementation("org.apache.pekko:pekko-cluster-typed_3:$pekkoVersion")
    implementation("org.apache.pekko:pekko-cluster-sharding-typed_3:$pekkoVersion")

    // Optional: Cluster Bootstrap & Management HTTP
    // Additionally, add a discovery method dependency (e.g., pekko-discovery-kubernetes-api_3)
    compileOnly("org.apache.pekko:pekko-management-cluster-bootstrap_3:${pekkoManagementVersion}")
    compileOnly("org.apache.pekko:pekko-management-cluster-http_3:${pekkoManagementVersion}")

    implementation("com.google.code.findbugs:jsr305")
    implementation("org.springframework.boot:spring-boot-starter")

    // Generates META-INF/spring-configuration-metadata.json from @ConfigurationProperties classes
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("com.fasterxml.jackson.core:jackson-databind")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// The configuration processor merges META-INF/additional-spring-configuration-metadata.json
// from the processed resources, so resources must be processed before compiling.
tasks.compileJava {
    inputs.files(tasks.processResources)
}

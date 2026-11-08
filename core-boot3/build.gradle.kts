plugins {
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

apply(plugin = "org.springframework.boot")
apply(plugin = "io.spring.dependency-management")

repositories {
    mavenCentral()
}

val pekkoVersion: String by project
val pekkoManagementVersion: String by project

dependencies {
    api("org.apache.pekko:pekko-actor-typed_3:$pekkoVersion")
    api("org.apache.pekko:pekko-cluster-typed_3:$pekkoVersion")
    api("org.apache.pekko:pekko-cluster-sharding-typed_3:$pekkoVersion")
    api("org.apache.pekko:pekko-serialization-jackson_3:$pekkoVersion")

    // Optional: Cluster Bootstrap & Management HTTP
    // Additionally, add a discovery method dependency (e.g., pekko-discovery-kubernetes-api_3)
    compileOnly("org.apache.pekko:pekko-management-cluster-bootstrap_3:${pekkoManagementVersion}")
    compileOnly("org.apache.pekko:pekko-management-cluster-http_3:${pekkoManagementVersion}")

    implementation("com.google.code.findbugs:jsr305")
    implementation("org.springframework.boot:spring-boot-starter")

    // Generates META-INF/spring-configuration-metadata.json from @ConfigurationProperties classes
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.apache.pekko:pekko-actor-testkit-typed_3")
    testImplementation("org.awaitility:awaitility")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// Disable bootJar and enable jar to fix Maven Central publication
tasks.bootJar {
    enabled = false
}

// The configuration processor merges META-INF/additional-spring-configuration-metadata.json
// from the processed resources, so resources must be processed before compiling.
tasks.compileJava {
    inputs.files(tasks.processResources)
}

tasks.jar {
    enabled = true
    archiveClassifier.set("")  // Ensure no classifier for the main JAR so that maven works
}

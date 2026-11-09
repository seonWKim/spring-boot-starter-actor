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
    // Cluster and management configurations apply only when these are on the application's classpath.
    compileOnly(project(":actor-cluster"))
    // Peer registry stores apply only when their module and Spring library are on the classpath.
    compileOnly(project(":actor-jdbc"))
    compileOnly(project(":actor-redis"))
    // The ActorStreams bean applies only when actor-streams is on the classpath.
    compileOnly(project(":actor-streams"))
    compileOnly("io.projectreactor:reactor-core")
    // Journal tables apply only when actor-persistence is on the classpath.
    compileOnly(project(":actor-persistence"))
    compileOnly("org.springframework:spring-jdbc")
    compileOnly("org.springframework.data:spring-data-redis")
    compileOnly("org.apache.pekko:pekko-management-cluster-bootstrap_3:${pekkoManagementVersion}")
    compileOnly("org.apache.pekko:pekko-management-cluster-http_3:${pekkoManagementVersion}")

    implementation("org.apache.pekko:pekko-actor-typed_3:$pekkoVersion")
    // Health indicators apply only when Actuator is on the application's classpath.
    compileOnly("org.springframework.boot:spring-boot-actuator")
    compileOnly(project(":actor-metrics"))
    compileOnly("io.micrometer:micrometer-core")
    compileOnly("io.micrometer:micrometer-observation")

    implementation("com.google.code.findbugs:jsr305")
    implementation("org.springframework.boot:spring-boot-starter")

    // Generates META-INF/spring-configuration-metadata.json from @ConfigurationProperties classes
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-starter-actuator")
    testImplementation("com.fasterxml.jackson.core:jackson-databind")
    // Tracing across tell and ask: an HTTP request through the OpenTelemetry bridge, spans kept in memory.
    testImplementation("org.springframework.boot:spring-boot-starter-web")
    testImplementation("io.micrometer:micrometer-tracing-bridge-otel")
    testImplementation("io.opentelemetry:opentelemetry-sdk-testing")
    testImplementation(project(":actor-metrics"))
    testImplementation("org.awaitility:awaitility")
    // spring.actor.cluster.join=discovery checks that Cluster Bootstrap is present.
    testRuntimeOnly("org.apache.pekko:pekko-management-cluster-bootstrap_3:${pekkoManagementVersion}")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// The configuration processor merges META-INF/additional-spring-configuration-metadata.json
// from the processed resources, so resources must be processed before compiling.
tasks.compileJava {
    inputs.files(tasks.processResources)
    // An incremental compile regenerates the metadata from the changed classes only, dropping
    // the keys of the others. This module is small: always compile it whole.
    options.isIncremental = false
}

// Overhead of the agent-free actor meters per message; the result is quoted in the metrics guide.
tasks.register<JavaExec>("actorMetersBenchmark") {
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("io.github.seonwkim.actor.autoconfigure.metrics.ActorMetersBenchmark")
}

// MonitoringFilesTest reads the dashboard, the alert rules and the meter docs: rerun it when they change.
tasks.test {
    inputs.dir(rootProject.file("monitoring")).withPropertyName("monitoring")
    inputs.files(
            rootProject.file("mkdocs/docs/examples/monitoring.md"),
            rootProject.file("mkdocs/docs/guides/production.md"))
        .withPropertyName("meterDocs")
}

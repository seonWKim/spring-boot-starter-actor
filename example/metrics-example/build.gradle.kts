import org.springframework.boot.gradle.tasks.run.BootRun

plugins {
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

apply(plugin = "org.springframework.boot")
apply(plugin = "io.spring.dependency-management")

dependencies {
    implementation(project(":actor-spring-boot-starter"))
    implementation(project(":actor-metrics"))

    implementation("org.springframework.boot:spring-boot-starter")
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("io.micrometer:micrometer-registry-prometheus")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// Run tests with the metrics agent so instrumentation is applied
tasks.named<Test>("test") {
    dependsOn(":actor-metrics:agentJar")
    val agentJar = project(":actor-metrics").tasks.named<Jar>("agentJar").get()
    jvmArgs("-javaagent:${agentJar.archiveFile.get().asFile.absolutePath}")
}

tasks.named<BootRun>("bootRun") {
    dependsOn(":actor-metrics:agentJar")
    val agentJar = project(":actor-metrics").tasks.named<Jar>("agentJar").get()
    jvmArgs = listOf(
        "-javaagent:${agentJar.archiveFile.get().asFile.absolutePath}",
        "-DACTOR_METRICS_DEBUG=true"
    )
}

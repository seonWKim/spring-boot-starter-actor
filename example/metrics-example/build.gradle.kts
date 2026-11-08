import org.springframework.boot.gradle.tasks.run.BootRun

plugins {
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

apply(plugin = "org.springframework.boot")
apply(plugin = "io.spring.dependency-management")

dependencies {
    implementation(project(":core-boot3"))
    implementation(project(":metrics"))

    implementation("org.springframework.boot:spring-boot-starter")
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("io.micrometer:micrometer-registry-prometheus")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// Run tests with the metrics agent so instrumentation is applied
tasks.named<Test>("test") {
    dependsOn(":metrics:agentJar")
    val agentJar = project(":metrics").tasks.named<Jar>("agentJar").get()
    jvmArgs("-javaagent:${agentJar.archiveFile.get().asFile.absolutePath}")
}

tasks.named<BootRun>("bootRun") {
    dependsOn(":metrics:agentJar")
    val agentJar = project(":metrics").tasks.named<Jar>("agentJar").get()
    jvmArgs = listOf(
        "-javaagent:${agentJar.archiveFile.get().asFile.absolutePath}",
        "-DACTOR_METRICS_DEBUG=true"
    )
}

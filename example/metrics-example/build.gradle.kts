import org.springframework.boot.gradle.tasks.run.BootRun

plugins {
    id("org.springframework.boot") version "2.7.0"
    id("io.spring.dependency-management") version "1.1.7"
}

apply(plugin = "org.springframework.boot")
apply(plugin = "io.spring.dependency-management")

dependencyManagement {
    imports {
        mavenBom("com.fasterxml.jackson:jackson-bom:2.17.3")
    }
}

dependencies {
    implementation(project(":core"))
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

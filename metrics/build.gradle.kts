plugins {
    id("java")
    id("java-library")
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

val bytebuddyVersion: String by project
val pekkoVersion: String by project

dependencies {
    implementation("net.bytebuddy:byte-buddy:${bytebuddyVersion}")
    implementation("net.bytebuddy:byte-buddy-agent:${bytebuddyVersion}")
    implementation("org.slf4j:slf4j-api:2.0.17")

    compileOnly("com.google.code.findbugs:jsr305:3.0.2")

    // Micrometer (compileOnly - users provide via Maven)
    compileOnly("io.micrometer:micrometer-core:1.11.0")

    // Pekko for compilation only (instrumentation needs Pekko classes)
    // Users bring their own Pekko at runtime
    compileOnly("org.apache.pekko:pekko-actor-typed_3:${pekkoVersion}")

    testImplementation("org.apache.pekko:pekko-actor-typed_3:${pekkoVersion}")
    testImplementation("org.apache.pekko:pekko-cluster-typed_3:${pekkoVersion}")
    testImplementation("org.apache.pekko:pekko-cluster-sharding-typed_3:${pekkoVersion}")
    testImplementation("org.apache.pekko:pekko-serialization-jackson_3:${pekkoVersion}")

    testImplementation("org.junit.jupiter:junit-jupiter-api:5.10.2")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.10.2")

    // Micrometer for testing
    testImplementation("io.micrometer:micrometer-core:1.11.0")

    // Add SLF4J Simple binding for tests
    testRuntimeOnly("org.slf4j:slf4j-simple:2.0.17")
}

// Create a fat JAR with all dependencies
tasks.register<Jar>("agentJar") {
    archiveBaseName.set("spring-boot-starter-actor-metrics")
    archiveClassifier.set("agent")

    manifest {
        attributes(
            "Premain-Class" to "io.github.seonwkim.metrics.agent.MetricsAgent",
            "Agent-Class" to "io.github.seonwkim.metrics.agent.MetricsAgent",
            "Can-Redefine-Classes" to "true",
            "Can-Retransform-Classes" to "true",
            "Implementation-Title" to "Actor Metrics Agent",
            "Implementation-Version" to project.version
        )
    }

    from(sourceSets.main.get().output)

    dependsOn(configurations.runtimeClasspath)
    from({
        configurations.runtimeClasspath.get().filter { it.name.endsWith("jar") }.map { zipTree(it) }
    })

    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

tasks.named("build") {
    dependsOn("agentJar")
}

tasks.test {
    dependsOn("agentJar")
    val agentJarFile = tasks.named<Jar>("agentJar").get().archiveFile.get().asFile
    jvmArgs = listOf(
        "-javaagent:${agentJarFile.absolutePath}"
    )

    testLogging {
        events("passed", "skipped", "failed", "standardOut", "standardError")
        showStandardStreams = true
    }
}

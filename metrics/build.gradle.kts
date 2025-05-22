plugins {
    id("java")
    id("java-library")
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

dependencies {
    implementation("net.bytebuddy:byte-buddy")
    implementation("net.bytebuddy:byte-buddy-agent")

    testImplementation("org.apache.pekko:pekko-actor-typed_3")
    testImplementation("org.apache.pekko:pekko-cluster-typed_3")
    testImplementation("org.apache.pekko:pekko-cluster-sharding-typed_3")
    testImplementation("org.apache.pekko:pekko-serialization-jackson_3")

    testImplementation("org.junit.jupiter:junit-jupiter-api:5.10.2")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.10.2")
}

// Create a fat JAR with all dependencies
tasks.register<Jar>("agentJar") {
    archiveClassifier.set("agent")

    manifest {
        attributes(
            "Premain-Class" to "io.github.seonwkim.metrics.MetricsAgent",
            "Agent-Class" to "io.github.seonwkim.metrics.MetricsAgent",
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

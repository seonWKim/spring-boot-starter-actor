import com.vanniktech.maven.publish.SonatypeHost
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent
import net.ltgt.gradle.errorprone.CheckSeverity
import net.ltgt.gradle.errorprone.errorprone

plugins {
    java
    `java-library`
    id("com.diffplug.spotless") version "6.13.0"
    id("com.vanniktech.maven.publish") version "0.31.0"
    id("net.ltgt.errorprone") version "3.1.0"
}

repositories {
    mavenCentral()
}

allprojects {
    group = project.findProperty("group") as String
    version = project.findProperty("version") as String
}

subprojects {
    apply(plugin = "java")
    apply(plugin = "java-library")
    apply(plugin = "com.diffplug.spotless")
    apply(plugin = "com.vanniktech.maven.publish")
    apply(plugin = "net.ltgt.errorprone")

    repositories {
        mavenCentral()
    }

    mavenPublishing {
        val isBoot3 = project.name.endsWith("boot3")
        coordinates(
            groupId = project.findProperty("group") as String,
            artifactId = (if (isBoot3) project.findProperty("artifactId-boot3") else project.findProperty("artifactId")) as String,
            version = project.findProperty("version") as String
        )

        pom {
            packaging = "jar"
            name.set(project.findProperty("pomName") as String)
            description.set(project.findProperty("pomDescription") as String)
            url.set(project.findProperty("pomUrl") as String)
            inceptionYear.set("2025")

            licenses {
                license {
                    name.set("The Apache License, Version 2.0")
                    url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                }
            }

            developers {
                developer {
                    id.set(project.findProperty("pomDeveloperId") as String)
                    name.set(project.findProperty("pomDeveloperName") as String)
                    email.set(project.findProperty("pomDeveloperEmail") as String)
                }
            }

            scm {
                connection.set("scm:git:git://github.com/seonwkim/spring-boot-starter-actor.git")
                developerConnection.set("scm:git:ssh://github.com/seonwkim/spring-boot-starter-actor.git")
                url.set(project.findProperty("pomUrl") as String)
            }
        }

        publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL)
        signAllPublications()
    }

    val pekkoVersion: String by project

    dependencies {
        implementation("org.apache.pekko:pekko-actor-typed_3:${pekkoVersion}")
        implementation("org.apache.pekko:pekko-cluster-typed_3:${pekkoVersion}")
        implementation("org.apache.pekko:pekko-cluster-sharding-typed_3:${pekkoVersion}")
        implementation("org.apache.pekko:pekko-serialization-jackson_3:${pekkoVersion}")
        implementation("com.google.code.findbugs:jsr305:3.0.2")
        errorprone("com.uber.nullaway:nullaway:0.10.26")
        errorprone("com.google.errorprone:error_prone_core:2.10.0")

        testImplementation("org.apache.pekko:pekko-actor-testkit-typed_3:$pekkoVersion")
        testImplementation("org.awaitility:awaitility:4.3.0")
    }

    tasks.withType<Test> {
        useJUnitPlatform()
        dependsOn("spotlessCheck")
    }

    tasks.named("build") {
        dependsOn("spotlessCheck")
    }
}

spotless {
    java {
        target("**/*.java")
        targetExclude(layout.buildDirectory.dir("**/*.java").get().asFile)
        removeUnusedImports()
        palantirJavaFormat("2.50.0")
        formatAnnotations()
        indentWithSpaces(4)
        trimTrailingWhitespace()
        endWithNewline()
    }
}

val runCoreTest = tasks.register<Exec>("runCoreTest") {
    commandLine("./gradlew", ":core:test")
}

val syncBoot3Sources = tasks.register<Exec>("syncBoot3Sources") {
    commandLine("./gradlew", ":core-boot3:syncAllBoot3Sources")
}

val runCoreBoot3Test = tasks.register<Exec>("runCoreBoot3Test") {
    commandLine("./gradlew", ":core-boot3:test")
}

val runMetricsTest = tasks.register<Exec>("runMetricsTest") {
    commandLine("./gradlew", ":metrics:test")
}

tasks.register("runTest") {
    group = "verification"
    description = "Run core and core-boot3 tests step-by-step"

    dependsOn(runCoreTest)
    dependsOn(syncBoot3Sources)
    dependsOn(runCoreBoot3Test)
    dependsOn(runMetricsTest)
}

subprojects {
    tasks.test {
        useJUnitPlatform()

        // Parallel test execution (use all available processors)
        maxParallelForks = Runtime.getRuntime().availableProcessors()

        minHeapSize = "512m"
        maxHeapSize = "2048m"

        jvmArgs = listOf(
            "-XX:+UseG1GC",
            "-XX:MaxGCPauseMillis=100"
        )

        failFast = false

        testLogging {
            // set options for log level LIFECYCLE
            events(
                TestLogEvent.FAILED,
                TestLogEvent.PASSED,
                TestLogEvent.SKIPPED,
            )

            // set options for log level DEBUG and INFO
            debug {
                events(
                    TestLogEvent.STARTED,
                    TestLogEvent.FAILED,
                    TestLogEvent.PASSED,
                    TestLogEvent.SKIPPED,
                    TestLogEvent.STANDARD_ERROR,
                    TestLogEvent.STANDARD_OUT
                )
                exceptionFormat = TestExceptionFormat.FULL
            }

            afterSuite(KotlinClosure2<TestDescriptor, TestResult, Unit>({ desc, result ->
                if (desc.parent == null) { // will match the outermost suite
                    val output =
                        "Results: ${result.resultType} (${result.testCount} tests, ${result.successfulTestCount} passed, ${result.failedTestCount} failed, ${result.skippedTestCount} skipped)"
                    val startItem = "|  "
                    val endItem = "  |"
                    val repeatLength = startItem.length + output.length + endItem.length
                    println(
                        "\n" + "-".repeat(repeatLength) + "\n" + startItem + output + endItem + "\n" + "-".repeat(
                            repeatLength
                        )
                    )
                }
            }))
        }
    }

    // Apply NullAway only to :core and :metrics subprojects
    if (project.name == "core" || project.name == "metrics") {
        tasks.withType<JavaCompile> {
            options.errorprone {
                // Let's select which checks to perform. NullAway is enough for now.
                disableAllChecks = true
                check("NullAway", CheckSeverity.ERROR)

                option("NullAway:AnnotatedPackages", "io.github.seonwkim")
            }
            if (name.lowercase().contains("test")) {
                options.errorprone {
                    disable("NullAway")
                }
            }
        }
    }
}


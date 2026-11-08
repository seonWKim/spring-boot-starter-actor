import com.vanniktech.maven.publish.MavenPublishBaseExtension
import com.vanniktech.maven.publish.SonatypeHost
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent
import net.ltgt.gradle.errorprone.CheckSeverity
import net.ltgt.gradle.errorprone.errorprone

plugins {
    java
    `java-library`
    id("com.diffplug.spotless") version "6.25.0"
    id("com.vanniktech.maven.publish") version "0.31.0" apply false
    id("net.ltgt.errorprone") version "3.1.0"
    id("org.springframework.boot") version "3.5.16" apply false
    id("io.spring.dependency-management") version "1.1.7" apply false
}

repositories {
    mavenCentral()
}

allprojects {
    group = project.findProperty("group") as String
    version = project.findProperty("version") as String
}

// The relocation POMs only carry <distributionManagement><relocation>: no code, no Java plugin.
val relocationTargets = mapOf(
    "spring-boot-starter-actor" to "actor-spring-boot-starter",
    "spring-boot-starter-actor_3" to "actor-spring-boot-starter",
    "spring-boot-starter-actor-metrics" to "actor-metrics",
)

fun MavenPom.commonPom(project: Project) {
    name.set(project.findProperty("pomName.${project.name}") as String)
    description.set(project.findProperty("pomDescription.${project.name}") as String)
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

configure(subprojects.filter { it.path.startsWith(":relocation:") }) {
    apply(plugin = "java-platform")
    apply(plugin = "com.vanniktech.maven.publish")

    // Maven follows <relocation>; Gradle does not, and prefers Gradle module metadata over the POM.
    // So publish the POM only, and let it also depend on the new artifact: Gradle builds that
    // still name the old coordinates get the new artifact transitively.
    extensions.configure<JavaPlatformExtension> { allowDependencies() }
    dependencies { "api"(project(":${relocationTargets.getValue(project.name)}")) }
    tasks.withType<GenerateModuleMetadata>().configureEach { enabled = false }

    extensions.configure<MavenPublishBaseExtension> {
        coordinates(
            groupId = project.findProperty("group") as String,
            artifactId = project.name,
            version = project.findProperty("version") as String
        )
        pom {
            packaging = "pom"
            commonPom(project)
            distributionManagement {
                relocation {
                    groupId.set(project.findProperty("group") as String)
                    artifactId.set(relocationTargets.getValue(project.name))
                    version.set(project.findProperty("version") as String)
                    message.set("Renamed in 1.0: see https://github.com/seonwkim/spring-boot-starter-actor/blob/main/CHANGELOG.md")
                }
            }
        }
        publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL)
        // Release builds pass the key (publish.yml); publishToMavenLocal works without one.
        if (project.hasProperty("signingInMemoryKey")) {
            signAllPublications()
        }
    }
}

configure(subprojects.filter { !it.path.startsWith(":relocation") }) {
    apply(plugin = "java")
    apply(plugin = "java-library")
    apply(plugin = "com.diffplug.spotless")

    // Java 21 is the baseline (ARCHITECTURE.md decision 3). Tests can run on a newer JVM
    // with -PtestJavaVersion=25 (CI matrix); the bytecode stays --release 21.
    extensions.configure<JavaPluginExtension> {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(21))
        }
    }
    tasks.withType<JavaCompile>().configureEach {
        options.release.set(21)
        // Keep constructor parameter names for Jackson creators and Spring (the Spring Boot
        // plugin adds this flag only to modules that apply it).
        options.compilerArgs.add("-parameters")
    }
    val testJavaVersion = project.findProperty("testJavaVersion") as String?
    if (testJavaVersion != null) {
        tasks.withType<Test>().configureEach {
            javaLauncher.set(
                project.extensions.getByType<JavaToolchainService>().launcherFor {
                    languageVersion.set(JavaLanguageVersion.of(testJavaVersion))
                }
            )
        }
    }

    // Only apply error-prone to core modules, not examples
    if (!project.path.startsWith(":example")) {
        apply(plugin = "net.ltgt.errorprone")
        tasks.withType<JavaCompile> {
            options.errorprone {
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

    repositories {
        mavenCentral()
    }

    // Only configure Maven publishing for modules that have properties defined
    // (i.e., not example modules)
    val artifactIdKey = "artifactId.${project.name}"
    if (project.findProperty(artifactIdKey) != null) {
        apply(plugin = "com.vanniktech.maven.publish")

        extensions.configure<MavenPublishBaseExtension> {
            coordinates(
                groupId = project.findProperty("group") as String,
                artifactId = project.findProperty(artifactIdKey) as String,
                version = project.findProperty("version") as String
            )

            pom {
                packaging = "jar"
                commonPom(project)
            }

            publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL)
            // Release builds pass the key (publish.yml); publishToMavenLocal works without one.
            if (project.hasProperty("signingInMemoryKey")) {
                signAllPublications()
            }
        }
    }

    val pekkoVersion: String by project

    dependencies {
        // Pekko is declared by each module that uses it (actor-api must never see it).
        implementation("com.google.code.findbugs:jsr305:3.0.2")

        // Only add error-prone dependencies for non-example projects
        if (!project.path.startsWith(":example")) {
            errorprone("com.uber.nullaway:nullaway:0.10.26")
            errorprone("com.google.errorprone:error_prone_core:2.10.0")
        }

        if (project.path == ":actor-core" || project.path.startsWith(":example")) {
            testImplementation("org.apache.pekko:pekko-actor-testkit-typed_3:$pekkoVersion")
        }
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

configure(subprojects.filter { !it.path.startsWith(":relocation") }) {
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

        // A hung test fails with its name instead of stalling the build.
        systemProperty("junit.jupiter.execution.timeout.default", "2m")

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

}


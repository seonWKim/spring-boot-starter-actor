// Kotlin extensions over actor-api: suspend functions and reified helpers. No Pekko, no logic of its
// own: every function calls the Java API (ARCHITECTURE.md, module table).
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm")
}

val springBootVersion: String by project

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
        // See actor-api's nullability annotations as Kotlin types, as Spring Boot's Kotlin projects do.
        freeCompilerArgs.addAll("-Xjsr305=strict")
    }
}

dependencies {
    api(project(":actor-api"))
    // Spring Boot manages the coroutines version. CompletionStage.await() is in kotlinx-coroutines-core
    // since 1.7 (kotlinx-coroutines-jdk8 is an empty artifact there).
    api(platform("org.springframework.boot:spring-boot-dependencies:${springBootVersion}"))
    api("org.jetbrains.kotlinx:kotlinx-coroutines-core")

    testImplementation(project(":actor-spring-boot-starter"))
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

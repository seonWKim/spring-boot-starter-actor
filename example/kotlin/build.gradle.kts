// The simple example, in Kotlin: suspend functions from actor-kotlin, a coroutine WebFlux controller.
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.springframework.boot")
    id("io.spring.dependency-management")
    kotlin("jvm")
    // Opens @Component and @Configuration classes for Spring, as in a start.spring.io Kotlin project.
    kotlin("plugin.spring")
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
        freeCompilerArgs.addAll("-Xjsr305=strict")
    }
}

dependencies {
    implementation(project(":actor-spring-boot-starter"))
    implementation(project(":actor-kotlin"))

    implementation("org.springframework.boot:spring-boot-starter-webflux")
    // Suspend controller functions in WebFlux.
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")
    implementation("org.jetbrains.kotlin:kotlin-reflect")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

plugins {
    `java-library`
}

dependencies {
    // Core metrics API
    api(project(":metrics"))

    // Micrometer core - the only external dependency
    api("io.micrometer:micrometer-core:1.10.0")

    // Logging
    implementation("org.slf4j:slf4j-api:2.0.7")

    // Testing
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}

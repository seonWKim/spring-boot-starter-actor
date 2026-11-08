// The public API: what users code against. No Pekko dependency at all (ARCHITECTURE.md rule 1).
val springBootVersion: String by project

dependencies {
    api(platform("org.springframework.boot:spring-boot-dependencies:${springBootVersion}"))
    api("org.springframework:spring-context")

    testImplementation(platform("org.springframework.boot:spring-boot-dependencies:${springBootVersion}"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

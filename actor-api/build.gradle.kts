// The public API: what users code against. No Pekko dependency at all (ARCHITECTURE.md rule 1).
plugins {
    // The SPI contract test suites and in-memory fakes, shared with the store modules' tests.
    `java-test-fixtures`
}

val springBootVersion: String by project

dependencies {
    api(platform("org.springframework.boot:spring-boot-dependencies:${springBootVersion}"))
    api("org.springframework:spring-context")
    // SpringBehaviorContext.getLog() returns an SLF4J Logger (Spring Boot's logging API).
    api("org.slf4j:slf4j-api")

    testFixturesApi(platform("org.springframework.boot:spring-boot-dependencies:${springBootVersion}"))
    testFixturesApi("org.junit.jupiter:junit-jupiter")
    testFixturesApi("org.assertj:assertj-core")
    testFixturesApi("org.awaitility:awaitility")

    testImplementation(platform("org.springframework.boot:spring-boot-dependencies:${springBootVersion}"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// The test fixtures are for this repository's tests only: keep them out of the published artifact.
val javaComponent = components["java"] as AdhocComponentWithVariants
javaComponent.withVariantsFromConfiguration(configurations["testFixturesApiElements"]) { skip() }
javaComponent.withVariantsFromConfiguration(configurations["testFixturesRuntimeElements"]) { skip() }
// The publish plugin adds the fixtures sources variant once the project is evaluated.
afterEvaluate {
    javaComponent.withVariantsFromConfiguration(configurations["testFixturesSourcesElements"]) { skip() }
}

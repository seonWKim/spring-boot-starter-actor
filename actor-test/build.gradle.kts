// Test support for applications: the @ActorTest slice, multi-node clusters, and the SPI contract
// suites with in-memory fakes. Users add it as a test dependency. No Pekko: it uses the public API.
dependencies {
    api(project(":actor-api"))
    api("org.springframework.boot:spring-boot-test-autoconfigure")
    api("org.springframework.boot:spring-boot-test")
    api("org.springframework:spring-test")
    api("org.junit.jupiter:junit-jupiter-api")
    // The contract suites assert with AssertJ and wait with Awaitility.
    implementation("org.assertj:assertj-core")
    implementation("org.awaitility:awaitility")

    testImplementation(project(":actor-spring-boot-starter-cluster"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    // On the classpath so the slice test can show it leaves web and data auto-configuration out.
    testImplementation("org.springframework.boot:spring-boot-starter-web")
    testImplementation("org.springframework.boot:spring-boot-starter-jdbc")
    testRuntimeOnly("com.h2database:h2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

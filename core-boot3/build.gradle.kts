plugins {
    id("org.springframework.boot") version "3.2.5"
    id("io.spring.dependency-management") version "1.1.7"
}

apply(plugin = "org.springframework.boot")
apply(plugin = "io.spring.dependency-management")

repositories {
    mavenCentral()
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencyManagement {
    imports {
        // pekko-serialization-jackson_3 require minimum 2.17.3 version of jackson
        mavenBom("com.fasterxml.jackson:jackson-bom:2.17.3")
    }
}

dependencies {
    api("org.apache.pekko:pekko-actor-typed_3")
    api("org.apache.pekko:pekko-cluster-typed_3")
    api("org.apache.pekko:pekko-cluster-sharding-typed_3")
    api("org.apache.pekko:pekko-serialization-jackson_3")

    api("org.springframework.boot:spring-boot-starter")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.apache.pekko:pekko-actor-testkit-typed_3")
    testImplementation("org.awaitility:awaitility")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

val clearCoreBoot3Sources by tasks.registering(Delete::class) {
    delete(
        "src/main/java", "src/main/resources",
        "src/test/java", "src/test/resources"
    )
}

fun CopySpec.replaceJavaxWithJakarta() {
    filteringCharset = "UTF-8"
    filter { line: String ->
        line.replace("javax.validation", "jakarta.validation")
            .replace("javax.annotation", "jakarta.annotation")
    }
}

val syncJavaToBoot3 by tasks.registering(Copy::class) {
    group = "boot3-porting"
    description = "Copy and transform main Java sources from `core`."

    dependsOn(clearCoreBoot3Sources)
    from("../core/src/main/java")
    into("src/main/java")
    replaceJavaxWithJakarta()
}

val syncResourcesToBoot3 by tasks.registering(Copy::class) {
    group = "boot3-porting"
    description = "Copy main resources from `core`."

    dependsOn(clearCoreBoot3Sources)
    from("../core/src/main/resources")
    into("src/main/resources")
}

val syncTestJavaToBoot3 by tasks.registering(Copy::class) {
    group = "boot3-porting"
    description = "Copy and transform test Java sources from `core`."

    dependsOn(clearCoreBoot3Sources)
    from("../core/src/test/java")
    into("src/test/java")
    replaceJavaxWithJakarta()
}

val syncTestResourcesToBoot3 by tasks.registering(Copy::class) {
    group = "boot3-porting"
    description = "Copy test resources from `core`."

    dependsOn(clearCoreBoot3Sources)
    from("../core/src/test/resources")
    into("src/test/resources")
}

val syncAllBoot3Sources by tasks.registering {
    group = "boot3-porting"
    description = "Sync all source and test files from `core` to `core-boot3`."

    dependsOn(
        syncJavaToBoot3,
        syncResourcesToBoot3,
        syncTestJavaToBoot3,
        syncTestResourcesToBoot3
    )
}

tasks.named<JavaCompile>("compileJava") {
    dependsOn(syncAllBoot3Sources)
}

tasks.named<ProcessResources>("processResources") {
    dependsOn(syncAllBoot3Sources)
}

tasks.withType<Test> {
    dependsOn(syncAllBoot3Sources)
}

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

dependencies {
    api("org.apache.pekko:pekko-actor-typed_3")
    api("org.apache.pekko:pekko-cluster-typed_3")
    api("org.apache.pekko:pekko-cluster-sharding-typed_3")
    api("org.springframework.boot:spring-boot-starter")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.apache.pekko:pekko-actor-testkit-typed_3")
    testImplementation("org.awaitility:awaitility")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

val generatedJavaDir = layout.buildDirectory.dir("generated/java")
val generatedResourceDir = layout.buildDirectory.dir("generated/resources")

val generateJava by tasks.registering(Copy::class) {
    from("../core/src/main/java")
    into(generatedJavaDir)
    filteringCharset = "UTF-8"
    filter { line: String ->
        line.replace("javax.validation", "jakarta.validation")
            .replace("javax.annotation", "jakarta.annotation")
    }
}

val generateResource by tasks.registering(Copy::class) {
    from("../core/src/main/resources")
    into(generatedResourceDir)
}

sourceSets {
    named("main") {
        java {
            srcDir(generatedJavaDir)
        }
        resources {
            srcDir(generatedResourceDir)
        }
    }
}

tasks.named<JavaCompile>("compileJava") {
    dependsOn(generateJava)
}

tasks.named<ProcessResources>("processResources") {
    dependsOn(generateResource)
}

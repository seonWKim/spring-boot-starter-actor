plugins {
    id("java")
    id("org.springframework.boot") version "2.7.18"
    id("io.spring.dependency-management") version "1.1.5"
}

repositories {
    mavenCentral()
}

val pekkoVersion: String by project

dependencyManagement {
    imports {
        mavenBom("com.fasterxml.jackson:jackson-bom:2.17.3")
    }
}

dependencies {
    implementation(project(":core"))
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.apache.pekko:pekko-actor-testkit-typed_3:$pekkoVersion")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

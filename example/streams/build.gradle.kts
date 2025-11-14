plugins {
    id("org.springframework.boot") version "2.7.0"
    id("io.spring.dependency-management") version "1.1.7"
}

apply(plugin = "org.springframework.boot")
apply(plugin = "io.spring.dependency-management")

val pekkoVersion: String by project

dependencyManagement {
    imports {
        // pekko-serialization-jackson_3 require minimum 2.17.3 version of jackson
        mavenBom("com.fasterxml.jackson:jackson-bom:2.17.3")
    }
}

dependencies {
    implementation(project(":core"))

    implementation("org.springframework.boot:spring-boot-starter")
    implementation("org.springframework.boot:spring-boot-starter-webflux")

    // Pekko Streams dependency
    implementation("org.apache.pekko:pekko-stream_3:${pekkoVersion}")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

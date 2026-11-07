plugins {
	id("org.springframework.boot") version "2.7.0"
	id("io.spring.dependency-management") version "1.1.7"
}

apply(plugin = "org.springframework.boot")
apply(plugin = "io.spring.dependency-management")

// Spring Boot 2.7 reads class files up to Java 17
java {
	sourceCompatibility = JavaVersion.VERSION_17
	targetCompatibility = JavaVersion.VERSION_17
}

dependencyManagement {
	imports {
		// pekko-serialization-jackson_3 requires jackson 2.17.3 or newer
		mavenBom("com.fasterxml.jackson:jackson-bom:2.17.3")
	}
}

// No LLM SDK on purpose: the example ships a fake LlmClient, so it runs without an API key or network.
dependencies {
	implementation(project(":core"))

	implementation("org.springframework.boot:spring-boot-starter")
	implementation("com.fasterxml.jackson.core:jackson-databind")

	testImplementation("org.springframework.boot:spring-boot-starter-test")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

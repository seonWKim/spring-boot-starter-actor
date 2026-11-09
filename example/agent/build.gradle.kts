plugins {
	id("org.springframework.boot")
	id("io.spring.dependency-management")
}

apply(plugin = "org.springframework.boot")
apply(plugin = "io.spring.dependency-management")

// No LLM SDK on purpose: the example ships a fake LlmClient, so it runs without an API key or network.
dependencies {
	implementation(project(":actor-spring-boot-starter-cluster"))

	implementation("org.springframework.boot:spring-boot-starter")
	implementation("com.fasterxml.jackson.core:jackson-databind")

	testImplementation("org.springframework.boot:spring-boot-starter-test")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

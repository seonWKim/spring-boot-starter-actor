plugins {
	id("org.springframework.boot")
	id("io.spring.dependency-management")
}

apply(plugin = "org.springframework.boot")
apply(plugin = "io.spring.dependency-management")

configurations {
	all {
		exclude(group = "org.springframework.boot", module = "spring-boot-starter-tomcat")
	}
}

dependencies {
	implementation(project(":core-boot3"))
	implementation(project(":metrics"))

	implementation("org.springframework.boot:spring-boot-starter")
	implementation("org.springframework.boot:spring-boot-starter-webflux")
	// Removed spring-boot-starter-websocket (blocking WebSocket, uses Tomcat)

	implementation("org.springframework.boot:spring-boot-starter-thymeleaf")
	implementation("org.springframework.boot:spring-boot-starter-actuator")
	implementation("io.micrometer:micrometer-registry-prometheus")  // Brings micrometer-core transitively
	implementation("com.fasterxml.jackson.core:jackson-databind")

	// BlockHound for detecting blocking calls
	implementation("io.projectreactor.tools:blockhound:1.0.13.RELEASE")

	testImplementation("org.springframework.boot:spring-boot-starter-test")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.named<org.springframework.boot.gradle.tasks.run.BootRun>("bootRun") {
	jvmArgs = listOf(
		"-javaagent:${rootProject.projectDir}/metrics/build/libs/spring-boot-starter-actor-metrics-${rootProject.version}-agent.jar"
	)
}

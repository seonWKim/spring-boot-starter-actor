plugins {
	id("org.springframework.boot") version "2.7.0"
	id("io.spring.dependency-management") version "1.1.7"
}

apply(plugin = "org.springframework.boot")
apply(plugin = "io.spring.dependency-management")

dependencyManagement {
	imports {
		// pekko-serialization-jackson_3 require minimum 2.17.3 version of jackson
		mavenBom("com.fasterxml.jackson:jackson-bom:2.17.3")
	}
}

configurations {
	all {
		exclude(group = "org.springframework.boot", module = "spring-boot-starter-tomcat")
	}
}

dependencies {
	implementation(project(":core"))
	implementation(project(":metrics"))

	implementation("org.springframework.boot:spring-boot-starter")
	implementation("org.springframework.boot:spring-boot-starter-webflux")
	// Removed spring-boot-starter-websocket (blocking WebSocket, uses Tomcat)

	implementation("org.springframework.boot:spring-boot-starter-thymeleaf")
	implementation("org.springframework.boot:spring-boot-starter-actuator")
	implementation("io.micrometer:micrometer-registry-prometheus")
	implementation("com.fasterxml.jackson.core:jackson-databind")

	// BlockHound for detecting blocking calls
	implementation("io.projectreactor.tools:blockhound:1.0.9.RELEASE")

	testImplementation("org.springframework.boot:spring-boot-starter-test")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.named<org.springframework.boot.gradle.tasks.run.BootRun>("bootRun") {
	jvmArgs = listOf(
		"-javaagent:${rootProject.projectDir}/metrics/build/libs/metrics-${rootProject.version}-agent.jar"
	)
}

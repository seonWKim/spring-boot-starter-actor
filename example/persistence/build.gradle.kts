plugins {
	id("org.springframework.boot") version "2.7.0"
	id("io.spring.dependency-management") version "1.1.7"
}

apply(plugin = "org.springframework.boot")
apply(plugin = "io.spring.dependency-management")

dependencyManagement {
	imports {
		mavenBom("com.fasterxml.jackson:jackson-bom:2.17.3")
	}
}

dependencies {
	implementation(project(":core"))

	// Spring Boot
	implementation("org.springframework.boot:spring-boot-starter")
	implementation("org.springframework.boot:spring-boot-starter-web")
	implementation("org.springframework.boot:spring-boot-starter-actuator")

	// JPA and Database
	implementation("org.springframework.boot:spring-boot-starter-data-jpa")
	implementation("com.h2database:h2")
	implementation("org.postgresql:postgresql")

	// Jackson for JSON serialization
	implementation("com.fasterxml.jackson.core:jackson-databind")
	implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")

	// Test
	testImplementation("org.springframework.boot:spring-boot-starter-test")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

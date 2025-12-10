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

dependencies {
	implementation(project(":core"))

	// Spring Boot
	implementation("org.springframework.boot:spring-boot-starter")
	implementation("org.springframework.boot:spring-boot-starter-webflux")
	implementation("org.springframework.boot:spring-boot-starter-actuator")

	// Configuration Properties
	annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")

	// Validation
	implementation("org.springframework.boot:spring-boot-starter-validation")

	// Testing
	testImplementation("org.springframework.boot:spring-boot-starter-test")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

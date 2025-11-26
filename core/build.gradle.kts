plugins {
	id("org.springframework.boot") version "2.7.0"
	id("io.spring.dependency-management") version "1.1.7"
}

apply(plugin = "org.springframework.boot")
apply(plugin = "io.spring.dependency-management")

repositories {
	mavenCentral()
}

java {
	sourceCompatibility = JavaVersion.VERSION_11
	targetCompatibility = JavaVersion.VERSION_11
}

// Not for maven publishing purposes
// pekko-serialization-jackson_3 require minimum 2.17.3 version of jackson, but spring-boot-starter-test
// brings a lower version of jackson, so we have to force the higher version
dependencyManagement {
	imports {
		mavenBom("com.fasterxml.jackson:jackson-bom:2.17.3")
	}
}

val pekkoVersion: String by project
val pekkoManagementVersion: String by project

dependencies {
	api("org.apache.pekko:pekko-actor-typed_3:$pekkoVersion")
	api("org.apache.pekko:pekko-cluster-typed_3:$pekkoVersion")
	api("org.apache.pekko:pekko-cluster-sharding-typed_3:$pekkoVersion")
	api("org.apache.pekko:pekko-serialization-jackson_3:$pekkoVersion")

	// Optional: Cluster Bootstrap & Management HTTP
	// Additionally, add a discovery method dependency (e.g., pekko-discovery-kubernetes-api_3)
	compileOnly("org.apache.pekko:pekko-management-cluster-bootstrap_3:$pekkoManagementVersion")
	compileOnly("org.apache.pekko:pekko-management-cluster-http_3:$pekkoManagementVersion")

	implementation("com.google.code.findbugs:jsr305")
	implementation("org.springframework.boot:spring-boot-starter")

	// Resilience patterns - Circuit Breaker
	compileOnly("io.github.resilience4j:resilience4j-spring-boot2:2.1.0")
	compileOnly("io.github.resilience4j:resilience4j-circuitbreaker:2.1.0")
	compileOnly("io.github.resilience4j:resilience4j-micrometer:2.1.0")

	// Resilience patterns - Deduplication (Caffeine for local cache)
	compileOnly("com.github.ben-manes.caffeine:caffeine:3.1.8")

	// Resilience patterns - Distributed Deduplication (Redis)
	compileOnly("org.springframework.boot:spring-boot-starter-data-redis-reactive")

	// Spring Boot Actuator for monitoring endpoints
	compileOnly("org.springframework.boot:spring-boot-starter-actuator")

	testImplementation("org.springframework.boot:spring-boot-starter-test")
	testImplementation("org.apache.pekko:pekko-actor-testkit-typed_3")
	testImplementation("org.awaitility:awaitility")
	testImplementation("io.github.resilience4j:resilience4j-spring-boot2:2.1.0")
	testImplementation("io.github.resilience4j:resilience4j-circuitbreaker:2.1.0")
	testImplementation("com.github.ben-manes.caffeine:caffeine:3.1.8")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// Disable bootJar task and enable regular jar task for library publishing
tasks.bootJar {
	enabled = false
}

tasks.jar {
	enabled = true
	archiveClassifier.set("")  // Ensure no classifier for the main JAR so that maven works
}

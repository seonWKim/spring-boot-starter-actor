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
dependencies {
	// Manually specifying version is required so that versions are specified in the pom.xml
	api("org.apache.pekko:pekko-actor-typed_3:$pekkoVersion")
	api("org.apache.pekko:pekko-cluster-typed_3:$pekkoVersion")
	api("org.apache.pekko:pekko-cluster-sharding-typed_3:$pekkoVersion")
	api("org.apache.pekko:pekko-serialization-jackson_3:$pekkoVersion")

	implementation("org.springframework.boot:spring-boot-starter")

	testImplementation("org.springframework.boot:spring-boot-starter-test")
	testImplementation("org.apache.pekko:pekko-actor-testkit-typed_3")
	testImplementation("org.awaitility:awaitility")
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

plugins {
	id("org.springframework.boot")
	id("io.spring.dependency-management")
}

apply(plugin = "org.springframework.boot")
apply(plugin = "io.spring.dependency-management")

repositories {
	mavenCentral()
}

dependencies {
	implementation(project(":actor-spring-boot-starter-cluster"))
	// Uses Pekko directly through unwrap(...): declare it, the starter does not export it.
	implementation("org.apache.pekko:pekko-actor-typed_3:${property("pekkoVersion")}")

	implementation("org.springframework.boot:spring-boot-starter")
	implementation("org.springframework.boot:spring-boot-starter-webflux")

	testImplementation("org.springframework.boot:spring-boot-starter-test")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

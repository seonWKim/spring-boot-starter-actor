plugins {
	id("org.springframework.boot")
	id("io.spring.dependency-management")
}

apply(plugin = "org.springframework.boot")
apply(plugin = "io.spring.dependency-management")

dependencies {
	implementation(project(":actor-spring-boot-starter"))

	implementation("org.springframework.boot:spring-boot-starter")
	implementation("org.springframework.boot:spring-boot-starter-webflux")

	// Logback dependencies (already included in spring-boot-starter)
	implementation("ch.qos.logback:logback-classic")

	// JSON logging support
	implementation("net.logstash.logback:logstash-logback-encoder:9.0")

	testImplementation("org.springframework.boot:spring-boot-starter-test")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

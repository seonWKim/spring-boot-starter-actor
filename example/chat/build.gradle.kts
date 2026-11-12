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
	implementation(project(":actor-spring-boot-starter-cluster"))
	// This example reads Pekko Cluster state directly (unwrap), so it declares Pekko Cluster itself.
	implementation("org.apache.pekko:pekko-cluster-sharding-typed_3:${property("pekkoVersion")}")
	implementation(project(":actor-metrics"))
	// The WebSocket endpoint connects each connection to a UserActor (ActorWebSocketHandler).
	implementation(project(":actor-streams"))

	// Kubernetes deployment (example/kubernetes): the cluster forms through Pekko Cluster Bootstrap,
	// which finds the other pods through the Kubernetes API.
	runtimeOnly("org.apache.pekko:pekko-management-cluster-bootstrap_3:${property("pekkoManagementVersion")}")
	runtimeOnly("org.apache.pekko:pekko-management-cluster-http_3:${property("pekkoManagementVersion")}")
	runtimeOnly("org.apache.pekko:pekko-discovery-kubernetes-api_3:${property("pekkoManagementVersion")}")

	implementation("org.springframework.boot:spring-boot-starter")
	implementation("org.springframework.boot:spring-boot-starter-webflux")
	// Removed spring-boot-starter-websocket (blocking WebSocket, uses Tomcat)

	implementation("org.springframework.boot:spring-boot-starter-thymeleaf")
	implementation("org.springframework.boot:spring-boot-starter-actuator")
	// Blue-green activation (example/kubernetes): the active generation in Redis, and an authenticated
	// generation endpoint. Off unless spring.actor.generation.color is set.
	implementation(project(":actor-redis"))
	implementation("org.springframework.boot:spring-boot-starter-data-redis")
	implementation("org.springframework.boot:spring-boot-starter-security")
	implementation("io.micrometer:micrometer-registry-prometheus")  // Brings micrometer-core transitively
	implementation("com.fasterxml.jackson.core:jackson-databind")

	// BlockHound for detecting blocking calls
	implementation("io.projectreactor.tools:blockhound:1.0.18.RELEASE")

	testImplementation("org.springframework.boot:spring-boot-starter-test")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// bootRun attaches the actor-metrics Java agent, built first.
tasks.named<org.springframework.boot.gradle.tasks.run.BootRun>("bootRun") {
	dependsOn(":actor-metrics:agentJar")
	val agentJar = project(":actor-metrics").tasks.named<Jar>("agentJar").get()
	jvmArgs = listOf("-javaagent:${agentJar.archiveFile.get().asFile.absolutePath}")
}

// The Docker image copies the one Boot jar from build/libs; the plain jar would make that copy ambiguous.
tasks.named<Jar>("jar") { enabled = false }

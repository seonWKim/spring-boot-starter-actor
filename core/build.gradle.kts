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

dependencyManagement {
	imports {
		// pekko-serialization-jackson_3 require minimum 2.17.3 version of jackson
		mavenBom("com.fasterxml.jackson:jackson-bom:2.17.3")
	}
}

dependencies {
	api("org.apache.pekko:pekko-actor-typed_3")
	api("org.apache.pekko:pekko-cluster-typed_3")
	api("org.apache.pekko:pekko-cluster-sharding-typed_3")
	api("org.apache.pekko:pekko-serialization-jackson_3")

	api("org.springframework.boot:spring-boot-starter")
	// TODO: I'm not sure to provide aop as default dependency, but for now it seems ok
	api("org.springframework.boot:spring-boot-starter-aop")

	testImplementation("org.springframework.boot:spring-boot-starter-test")
	testImplementation("org.apache.pekko:pekko-actor-testkit-typed_3")
	testImplementation("org.awaitility:awaitility")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

plugins {
	java
	id("org.springframework.boot") version "2.7.0"
	id("io.spring.dependency-management") version "1.1.7"
	id("com.diffplug.spotless") version "6.13.0"
}

java {
	sourceCompatibility = JavaVersion.VERSION_1_8
	targetCompatibility = JavaVersion.VERSION_1_8
}

repositories {
	mavenCentral()
}

allprojects {
	group = "org.github.seonwkim"
	version = "0.0.1-SNAPSHOT"
}

subprojects {
	apply(plugin = "java")
	apply(plugin = "org.springframework.boot")
	apply(plugin = "io.spring.dependency-management")
	apply(plugin = "com.diffplug.spotless")

	repositories {
		mavenCentral()
	}

	dependencies {
		implementation("org.apache.pekko:pekko-actor-typed_3:1.1.3")
		implementation("org.apache.pekko:pekko-cluster-typed_3:1.1.3")
		implementation("org.apache.pekko:pekko-cluster-sharding-typed_3:1.1.3")
		implementation("org.springframework.boot:spring-boot-starter")

		testImplementation("org.apache.pekko:pekko-actor-testkit-typed_3:1.1.3")
		testImplementation("org.springframework.boot:spring-boot-starter-test")
		testImplementation("org.awaitility:awaitility:4.3.0")
		testRuntimeOnly("org.junit.platform:junit-platform-launcher")
	}

	tasks.withType<Test> {
		useJUnitPlatform()
	}
}

spotless {
	java {
		target("**/*.java")
		targetExclude(layout.buildDirectory.dir("**/*.java").get().asFile)
		removeUnusedImports()
		googleJavaFormat("1.7") // or use eclipse().configFile("path/to/eclipse-format.xml")
		indentWithTabs(2)
	}
}

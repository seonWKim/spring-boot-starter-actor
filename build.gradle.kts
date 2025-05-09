import com.vanniktech.maven.publish.SonatypeHost

plugins {
	java
	`java-library`
	id("org.springframework.boot") version "2.7.0"
	id("io.spring.dependency-management") version "1.1.7"
	id("com.diffplug.spotless") version "6.13.0"
	id("com.vanniktech.maven.publish") version "0.31.0"
}

java {
	sourceCompatibility = JavaVersion.VERSION_11
	targetCompatibility = JavaVersion.VERSION_11
}

repositories {
	mavenCentral()
}

allprojects {
	group = "io.github.seonwkim"
	version = "0.0.2"
}

subprojects {
	apply(plugin = "java")
	apply(plugin = "java-library")
	apply(plugin = "org.springframework.boot")
	apply(plugin = "io.spring.dependency-management")
	apply(plugin = "com.diffplug.spotless")
	apply(plugin = "com.vanniktech.maven.publish")

	repositories {
		mavenCentral()
	}

	mavenPublishing {
		coordinates(
			groupId = project.group.toString(),
			artifactId = rootProject.name,
			version = project.version.toString()
		)

		pom {
			name.set("Spring Boot Starter Actor")
			description.set("A library that integrates Spring Boot with the actor model using Pekko.")
			inceptionYear.set("2025")
			url.set("https://github.com/seonwkim/spring-boot-starter-actor")

			licenses {
				license {
					name.set("The Apache License, Version 2.0")
					url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
				}
			}

			developers {
				developer {
					id.set("seonwkim")
					name.set("Seon Woo Kim")
					email.set("seonwoo960000.kim@gmail.com")
				}
			}

			scm {
				connection.set("scm:git:git://github.com/seonwkim/spring-boot-starter-actor.git")
				developerConnection.set("scm:git:ssh://github.com/seonwkim/spring-boot-starter-actor.git")
				url.set("https://github.com/seonwkim/spring-boot-starter-actor")
			}
		}

		publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL)

		signAllPublications()
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

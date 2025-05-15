repositories {
	mavenCentral()
}

java {
	sourceCompatibility = JavaVersion.VERSION_11
	targetCompatibility = JavaVersion.VERSION_11
}

dependencies {
	api("org.apache.pekko:pekko-actor-typed_3")
	api("org.apache.pekko:pekko-cluster-typed_3")
    api("io.micrometer:micrometer-core:1.12.5")

	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

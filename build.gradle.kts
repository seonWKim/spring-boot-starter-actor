import com.vanniktech.maven.publish.SonatypeHost

plugins {
    java
    `java-library`
    id("com.diffplug.spotless") version "6.13.0"
    id("com.vanniktech.maven.publish") version "0.31.0"
}

repositories {
    mavenCentral()
}

allprojects {
    group = project.findProperty("group") as String
    version = project.findProperty("version") as String
}

subprojects {
    apply(plugin = "java")
    apply(plugin = "java-library")
    apply(plugin = "com.diffplug.spotless")
    apply(plugin = "com.vanniktech.maven.publish")

    repositories {
        mavenCentral()
    }

    mavenPublishing {
        val isBoot3 = project.name.endsWith("boot3")
        coordinates(
            groupId = project.findProperty("group") as String,
            artifactId = (if (isBoot3) project.findProperty("artifactId-boot3") else project.findProperty("artifactId")) as String,
            version = project.findProperty("version") as String
        )

        pom {
            name.set(project.findProperty("pomName") as String)
            description.set(project.findProperty("pomDescription") as String)
            url.set(project.findProperty("pomUrl") as String)
            inceptionYear.set("2025")

            licenses {
                license {
                    name.set("The Apache License, Version 2.0")
                    url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                }
            }

            developers {
                developer {
                    id.set(project.findProperty("pomDeveloperId") as String)
                    name.set(project.findProperty("pomDeveloperName") as String)
                    email.set(project.findProperty("pomDeveloperEmail") as String)
                }
            }

            scm {
                connection.set("scm:git:git://github.com/seonwkim/spring-boot-starter-actor.git")
                developerConnection.set("scm:git:ssh://github.com/seonwkim/spring-boot-starter-actor.git")
                url.set(project.findProperty("pomUrl") as String)
            }
        }

        publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL)
        signAllPublications()
    }

    val pekkoVersion = "1.1.3"
    dependencies {
        constraints {
            api("org.apache.pekko:pekko-bom_3:$pekkoVersion")
        }

        implementation(platform("org.apache.pekko:pekko-bom_3:$pekkoVersion"))
        implementation("org.apache.pekko:pekko-cluster-typed_3")
        implementation("org.apache.pekko:pekko-cluster-sharding-typed_3")
        implementation("org.apache.pekko:pekko-serialization-jackson_3")

        testImplementation("org.apache.pekko:pekko-actor-testkit-typed_3")
        testImplementation("org.awaitility:awaitility:4.3.0")
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

val runCoreTest = tasks.register<Exec>("runCoreTest") {
    commandLine("./gradlew", ":core:test")
}

val syncBoot3Sources = tasks.register<Exec>("syncBoot3Sources") {
    commandLine("./gradlew", ":core-boot3:syncAllBoot3Sources")
}

val runCoreBoot3Test = tasks.register<Exec>("runCoreBoot3Test") {
    commandLine("./gradlew", ":core-boot3:test")
}

tasks.register("runTest") {
    group = "verification"
    description = "Run core and core-boot3 tests step-by-step"

    dependsOn(runCoreTest)
    dependsOn(syncBoot3Sources)
    dependsOn(runCoreBoot3Test)
}

// Manifests only. The test renders the kustomize overlays (skipped without kubectl).
val springBootVersion: String by project

dependencies {
    testImplementation(platform("org.springframework.boot:spring-boot-dependencies:${springBootVersion}"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.assertj:assertj-core")
    testImplementation("org.yaml:snakeyaml")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// The test reads the manifests, not compiled classes: rerun it when they change.
tasks.test {
    inputs.dir("base").withPropertyName("base")
    inputs.dir("components").withPropertyName("components")
    inputs.dir("overlays").withPropertyName("overlays")
}

// Manifests only. The tests render the kustomize overlays and run the rollout scripts' jq filters
// on them (skipped without kubectl or jq).
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
    inputs.dir("scripts").withPropertyName("scripts")
}

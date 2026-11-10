// io.github.seonwkim:actor-bom: every published module at the project version, so users set the
// version once. A module is published when gradle.properties has artifactId.<module>; the root build
// applies java-platform and the publishing setup. scripts/release/check-poms.py fails when a
// published module is missing from this BOM.
val publishedModules = rootProject.subprojects.filter { findProperty("artifactId.${it.name}") != null }

dependencies {
    constraints {
        publishedModules.forEach { "api"(it) }
    }
}

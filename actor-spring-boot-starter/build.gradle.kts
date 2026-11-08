// Empty starter: picks the modules an application needs. Pekko is an implementation detail
// of actor-core and is not on the application's compile classpath.
dependencies {
    api(project(":actor-api"))
    api(project(":actor-core"))
    api(project(":actor-autoconfigure"))
}

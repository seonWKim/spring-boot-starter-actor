// Applies GraalVM Native Build Tools to the example apps named in -PnativeExamples (comma-separated
// project paths), for the native image smoke test (.github/workflows/native-image.yml). The plugin is
// on the build's classpath through the root build (apply false), so Spring Boot's plugin sees it and
// runs processAot before nativeCompile. beforeProject: the root build, and with it the plugin, is
// loaded by then.
gradle.beforeProject {
    val paths = (findProperty("nativeExamples") as String?)?.split(",")?.map { it.trim() } ?: emptyList()
    if (path in paths) {
        pluginManager.apply("org.graalvm.buildtools.native")
    }
}

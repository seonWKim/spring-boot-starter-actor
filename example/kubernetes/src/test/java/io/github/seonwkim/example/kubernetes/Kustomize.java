package io.github.seonwkim.example.kubernetes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import org.yaml.snakeyaml.Yaml;

/** Renders a kustomization with {@code kubectl kustomize} and reads the result. */
final class Kustomize {

    private Kustomize() {}

    /** Renders {@code dir} (relative to the module); skips the test when kubectl is not installed. */
    @SuppressWarnings("unchecked")
    static List<Map<String, Object>> render(String dir) throws IOException, InterruptedException {
        Process process;
        try {
            process = new ProcessBuilder("kubectl", "kustomize", dir)
                    .directory(new File(System.getProperty("user.dir")))
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();
        } catch (IOException e) {
            assumeTrue(false, "kubectl is not installed");
            throw e;
        }
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(process.waitFor(60, TimeUnit.SECONDS)).isTrue();
        assertThat(process.exitValue()).as("kubectl kustomize " + dir).isZero();
        List<Map<String, Object>> resources = new ArrayList<>();
        new Yaml().loadAll(output).forEach(document -> resources.add((Map<String, Object>) document));
        return resources;
    }

    static Map<String, Object> resource(List<Map<String, Object>> resources, String kind, String name) {
        return resources.stream()
                .filter(r -> kind.equals(r.get("kind")) && name.equals(get(r, "metadata", "name")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no " + kind + " " + name));
    }

    /** Walks maps by key and lists by index. */
    @SuppressWarnings("unchecked")
    static Object get(Object node, Object... path) {
        Object current = node;
        for (Object step : path) {
            current = step instanceof Integer index
                    ? ((List<Object>) current).get(index)
                    : ((Map<String, Object>) Objects.requireNonNull(current, "missing " + step)).get(step);
        }
        return current;
    }

    /** The ConfigMap's {@code application.yaml}, parsed. */
    @SuppressWarnings("unchecked")
    static Map<String, Object> applicationYaml(Map<String, Object> configMap) {
        return (Map<String, Object>) new Yaml().load((String) get(configMap, "data", "application.yaml"));
    }
}

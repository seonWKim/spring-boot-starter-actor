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
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.yaml.snakeyaml.Yaml;

/**
 * The discovery overlays render with {@code kubectl kustomize}, need no RBAC, and open only the
 * cluster ports between pods plus app to store. Skipped when kubectl is not installed.
 */
class DiscoveryOverlaysTest {

    static Stream<Arguments> overlays() {
        return Stream.of(Arguments.of("discovery-jdbc", "jdbc", 5432), Arguments.of("discovery-redis", "redis", 6379));
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> render(String overlay) throws IOException, InterruptedException {
        Process process;
        try {
            process = new ProcessBuilder("kubectl", "kustomize", "overlays/" + overlay)
                    .directory(new File(System.getProperty("user.dir")))
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();
        } catch (IOException e) {
            assumeTrue(false, "kubectl is not installed");
            throw e;
        }
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(process.waitFor(60, TimeUnit.SECONDS)).isTrue();
        assertThat(process.exitValue())
                .as("kubectl kustomize overlays/" + overlay)
                .isZero();
        List<Map<String, Object>> resources = new ArrayList<>();
        new Yaml().loadAll(output).forEach(document -> resources.add((Map<String, Object>) document));
        return resources;
    }

    private static Map<String, Object> resource(List<Map<String, Object>> resources, String kind, String name) {
        return resources.stream()
                .filter(r -> kind.equals(r.get("kind")) && name.equals(get(r, "metadata", "name")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no " + kind + " " + name));
    }

    @SuppressWarnings("unchecked")
    private static Object get(Object node, Object... path) {
        Object current = node;
        for (Object step : path) {
            current = step instanceof Integer index
                    ? ((List<Object>) current).get(index)
                    : ((Map<String, Object>) Objects.requireNonNull(current)).get(step);
        }
        return current;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> ports(Map<String, Object> rule) {
        return ((List<Map<String, Object>>) rule.get("ports"))
                .stream().map(port -> port.get("port")).toList();
    }

    @ParameterizedTest
    @MethodSource("overlays")
    void theOverlayNeedsNoRbac(String overlay, String method, int storePort) throws Exception {
        List<Map<String, Object>> resources = render(overlay);

        assertThat(resources)
                .extracting(r -> r.get("kind"))
                .doesNotContain("Role", "RoleBinding", "ClusterRole", "ClusterRoleBinding");
        Map<String, Object> deployment = resource(resources, "Deployment", "spring-actor");
        assertThat(get(deployment, "spec", "template", "spec", "automountServiceAccountToken"))
                .isEqualTo(false);
        assertThat(String.valueOf(
                        get(resource(resources, "ConfigMap", "spring-actor-config"), "data", "application.yaml")))
                .contains("join: discovery")
                .doesNotContain("kubernetes-api");
        assertThat(String.valueOf(get(deployment, "spec", "template", "spec", "containers", 0, "env")))
                .contains("name=SPRING_ACTOR_CLUSTER_DISCOVERY_METHOD, value=" + method);
    }

    @ParameterizedTest
    @MethodSource("overlays")
    @SuppressWarnings("unchecked")
    void podsReachEachOtherOnlyOnRemotingAndManagementAndTheStore(String overlay, String method, int storePort)
            throws Exception {
        List<Map<String, Object>> resources = render(overlay);

        Map<String, Object> cluster = resource(resources, "NetworkPolicy", "spring-actor");
        List<Map<String, Object>> ingress = (List<Map<String, Object>>) get(cluster, "spec", "ingress");
        List<Map<String, Object>> egress = (List<Map<String, Object>>) get(cluster, "spec", "egress");
        assertThat(ports(ingress.get(0))).containsExactlyInAnyOrder(25520, 7626);
        assertThat(ports(egress.get(0))).containsExactlyInAnyOrder(25520, 7626);
        assertThat(egress).allSatisfy(rule -> assertThat(ports(rule)).doesNotContain(443));

        Map<String, Object> store = resource(resources, "NetworkPolicy", "spring-actor-store");
        assertThat(ports(((List<Map<String, Object>>) get(store, "spec", "egress")).get(0)))
                .containsExactly(storePort);
        assertThat(get(
                        resource(resources, "Deployment", "spring-actor"),
                        "spec",
                        "template",
                        "spec",
                        "containers",
                        0,
                        "ports"))
                .asString()
                .contains("containerPort=25520", "containerPort=7626")
                .doesNotContain("containerPort=2551,", "containerPort=8558");
    }
}

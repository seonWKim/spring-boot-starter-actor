package io.github.seonwkim.example.kubernetes;

import static io.github.seonwkim.example.kubernetes.Kustomize.get;
import static io.github.seonwkim.example.kubernetes.Kustomize.render;
import static io.github.seonwkim.example.kubernetes.Kustomize.resource;
import static java.util.stream.Collectors.joining;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.yaml.snakeyaml.Yaml;

/**
 * The pods that {@code scripts/blue-green.sh} and {@code scripts/canary.sh} start are covered by the
 * overlay's NetworkPolicies: remoting (25520) and management (7626) open only between pods of the
 * same color (blue and its canary, or green), and to nothing else; HTTP (8080) is open to any client;
 * Actuator (8081) only to the monitoring namespace. Runs the scripts' own jq filters on the rendered
 * manifests. Skipped when kubectl or jq is not installed.
 */
class RolloutNetworkPoliciesTest {

    private static final String NAMESPACE = "spring-actor";
    private static final List<Integer> CLUSTER_PORTS = List.of(25520, 7626);
    private static final int HTTP_PORT = 8080;
    private static final int ACTUATOR_PORT = 8081;
    private static final Pod OTHER_POD = new Pod(Map.of("app", "something-else"), NAMESPACE);
    private static final Pod OTHER_NAMESPACE_POD = new Pod(Map.of("app", "something-else"), "other");
    private static final Pod PROMETHEUS = new Pod(Map.of("app.kubernetes.io/name", "prometheus"), "monitoring");

    /** A pod's labels and the namespace it runs in. */
    private record Pod(Map<String, Object> labels, String namespace) {}

    /** The overlay's policies plus green's copies, and a blue, a green and a canary pod. */
    private record Rollout(List<Map<String, Object>> policies, Pod blue, Pod green, Pod canary) {
        List<Pod> pods() {
            return List.of(blue, green, canary);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"overlays/discovery-jdbc", "overlays/discovery-redis"})
    @SuppressWarnings("unchecked")
    void clusterPortsOpenOnlyBetweenPodsOfTheSameColor(String overlay) throws Exception {
        Rollout rollout = rollout(overlay);

        for (Pod pod : rollout.pods()) {
            assertThat(rollout.policies())
                    .as("a NetworkPolicy restricting ingress and egress selects %s", pod)
                    .anySatisfy(policy -> {
                        assertThat(selects(policy, pod)).isTrue();
                        assertThat((List<String>) get(policy, "spec", "policyTypes"))
                                .contains("Ingress", "Egress");
                    });
        }
        List<Map<String, Object>> policies = rollout.policies();
        for (int port : CLUSTER_PORTS) {
            assertThat(reaches(policies, rollout.blue(), rollout.canary(), port))
                    .as("blue -> canary on %d", port)
                    .isTrue();
            assertThat(reaches(policies, rollout.canary(), rollout.blue(), port))
                    .as("canary -> blue on %d", port)
                    .isTrue();
            assertThat(reaches(policies, rollout.green(), rollout.green(), port))
                    .as("green -> green on %d", port)
                    .isTrue();
            for (Pod bluish : List.of(rollout.blue(), rollout.canary())) {
                assertThat(reaches(policies, rollout.green(), bluish, port))
                        .as("green -> %s on %d", bluish, port)
                        .isFalse();
                assertThat(reaches(policies, bluish, rollout.green(), port))
                        .as("%s -> green on %d", bluish, port)
                        .isFalse();
            }
            for (Pod pod : rollout.pods()) {
                assertThat(reaches(policies, OTHER_POD, pod, port))
                        .as("other pod -> %s on %d", pod, port)
                        .isFalse();
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"overlays/discovery-jdbc", "overlays/discovery-redis"})
    void actuatorOpensOnlyToTheMonitoringNamespaceWhileHttpStaysOpen(String overlay) throws Exception {
        Rollout rollout = rollout(overlay);
        List<Map<String, Object>> policies = rollout.policies();

        for (Pod pod : rollout.pods()) {
            assertThat(reaches(policies, OTHER_POD, pod, HTTP_PORT))
                    .as("other pod -> %s on HTTP", pod)
                    .isTrue();
            assertThat(reaches(policies, PROMETHEUS, pod, ACTUATOR_PORT))
                    .as("Prometheus -> %s on Actuator", pod)
                    .isTrue();
            assertThat(reaches(policies, OTHER_POD, pod, ACTUATOR_PORT))
                    .as("other pod -> %s on Actuator", pod)
                    .isFalse();
            assertThat(reaches(policies, OTHER_NAMESPACE_POD, pod, ACTUATOR_PORT))
                    .as("pod in another namespace -> %s on Actuator", pod)
                    .isFalse();
        }
    }

    @SuppressWarnings("unchecked")
    private static Rollout rollout(String overlay) throws Exception {
        List<Map<String, Object>> resources = render(overlay);
        Map<String, Object> blue = resource(resources, "Deployment", "spring-actor");
        List<Map<String, Object>> policies = new ArrayList<>(resources.stream()
                .filter(r -> "NetworkPolicy".equals(r.get("kind")))
                .toList());

        Map<String, Object> green = (Map<String, Object>)
                jq("blue-green-deployment.jq", blue, "--arg", "name", "spring-actor-green", "--arg", "image", "v2");
        Map<String, Object> greenPolicies = (Map<String, Object>) jq(
                "blue-green-policies.jq",
                Map.of("items", policies),
                "--arg",
                "blue",
                "spring-actor",
                "--arg",
                "green",
                "spring-actor-green");
        policies.addAll((List<Map<String, Object>>) greenPolicies.get("items"));
        Map<String, Object> canary = (Map<String, Object>) jq(
                "canary-deployment.jq",
                blue,
                "--arg",
                "name",
                "spring-actor-canary",
                "--arg",
                "image",
                "v2",
                "--argjson",
                "replicas",
                "1");
        return new Rollout(policies, pod(blue), pod(green), pod(canary));
    }

    @SuppressWarnings("unchecked")
    private static Pod pod(Map<String, Object> deployment) {
        return new Pod(
                (Map<String, Object>) get(deployment, "spec", "template", "metadata", "labels"),
                String.valueOf(get(deployment, "metadata", "namespace")));
    }

    /**
     * Whether {@code from} may open a connection to {@code to} on {@code port}: egress allowed by the
     * policies selecting {@code from} and ingress allowed by those selecting {@code to}. An IP block
     * counts as allowing (the worst case).
     */
    private static boolean reaches(List<Map<String, Object>> policies, Pod from, Pod to, int port) {
        return allows(policies, from, "Egress", "egress", "to", to, port)
                && allows(policies, to, "Ingress", "ingress", "from", from, port);
    }

    @SuppressWarnings("unchecked")
    private static boolean allows(
            List<Map<String, Object>> policies,
            Pod pod,
            String type,
            String rulesKey,
            String peersKey,
            Pod peer,
            int port) {
        List<Map<String, Object>> selecting = policies.stream()
                .filter(policy -> selects(policy, pod))
                .filter(policy -> ((List<String>) get(policy, "spec", "policyTypes")).contains(type))
                .toList();
        if (selecting.isEmpty()) {
            return true; // no policy isolates the pod in this direction
        }
        return selecting.stream().anyMatch(policy -> ((List<Map<String, Object>>)
                        ((Map<String, Object>) policy.get("spec")).getOrDefault(rulesKey, List.of()))
                .stream()
                        .anyMatch(
                                rule -> opensPort(rule, port) && admits(rule.get(peersKey), namespace(policy), peer)));
    }

    @SuppressWarnings("unchecked")
    private static boolean opensPort(Map<String, Object> rule, int port) {
        List<Map<String, Object>> ports = (List<Map<String, Object>>) rule.get("ports");
        return ports == null
                || ports.isEmpty()
                || ports.stream().anyMatch(p -> Integer.valueOf(port).equals(p.get("port")));
    }

    /**
     * Whether a rule's peers include {@code pod}. A peer with only a pod selector means pods in the
     * policy's namespace; with a namespace selector, pods in the namespaces it selects (by their
     * {@code kubernetes.io/metadata.name} label); with both, both must match.
     */
    @SuppressWarnings("unchecked")
    private static boolean admits(Object peers, String policyNamespace, Pod pod) {
        if (peers == null || ((List<Object>) peers).isEmpty()) {
            return true; // no peers listed: any source or destination
        }
        return ((List<Map<String, Object>>) peers).stream().anyMatch(peer -> {
            if (peer.containsKey("ipBlock")) {
                return true;
            }
            boolean namespaceMatches = peer.containsKey("namespaceSelector")
                    ? matches(peer.get("namespaceSelector"), Map.of("kubernetes.io/metadata.name", pod.namespace()))
                    : pod.namespace().equals(policyNamespace);
            boolean podMatches = !peer.containsKey("podSelector") || matches(peer.get("podSelector"), pod.labels());
            return namespaceMatches && podMatches;
        });
    }

    private static boolean selects(Map<String, Object> policy, Pod pod) {
        return namespace(policy).equals(pod.namespace()) && matches(get(policy, "spec", "podSelector"), pod.labels());
    }

    private static String namespace(Map<String, Object> policy) {
        Object namespace = get(policy, "metadata", "namespace");
        return namespace == null ? NAMESPACE : namespace.toString();
    }

    @SuppressWarnings("unchecked")
    private static boolean matches(Object selector, Map<String, Object> labels) {
        Map<String, Object> matchLabels = (Map<String, Object>) ((Map<String, Object>) selector).get("matchLabels");
        assertThat(((Map<String, Object>) selector).get("matchExpressions"))
                .as("matchExpressions are not evaluated here")
                .isNull();
        return matchLabels == null
                || matchLabels.entrySet().stream().allMatch(e -> e.getValue().equals(labels.get(e.getKey())));
    }

    /** Runs {@code scripts/<filter>} on {@code input}; skips the test when jq is not installed. */
    private static Object jq(String filter, Object input, String... args) throws IOException, InterruptedException {
        List<String> command = new ArrayList<>(List.of("jq", "-c"));
        command.addAll(List.of(args));
        command.addAll(List.of("-f", "scripts/" + filter));
        Process process;
        try {
            process = new ProcessBuilder(command)
                    .directory(new File(System.getProperty("user.dir")))
                    .redirectError(ProcessBuilder.Redirect.INHERIT)
                    .start();
        } catch (IOException e) {
            assumeTrue(false, "jq is not installed");
            throw e;
        }
        try (var stdin = process.getOutputStream()) {
            stdin.write(json(input).getBytes(StandardCharsets.UTF_8));
        }
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(process.waitFor(60, TimeUnit.SECONDS)).isTrue();
        assertThat(process.exitValue()).as("jq -f scripts/" + filter).isZero();
        return new Yaml().load(output); // JSON is YAML
    }

    private static String json(Object value) {
        if (value instanceof Map<?, ?> map) {
            return map.entrySet().stream()
                    .map(e -> json(String.valueOf(e.getKey())) + ":" + json(e.getValue()))
                    .collect(joining(",", "{", "}"));
        }
        if (value instanceof List<?> list) {
            return list.stream().map(RolloutNetworkPoliciesTest::json).collect(joining(",", "[", "]"));
        }
        if (value instanceof String string) {
            StringBuilder quoted = new StringBuilder("\"");
            string.chars()
                    .forEach(c -> quoted.append(
                            c == '"' || c == '\\'
                                    ? "\\" + (char) c
                                    : c < 0x20 ? String.format("\\u%04x", c) : (char) c));
            return quoted.append('"').toString();
        }
        return String.valueOf(value); // numbers, booleans, null
    }
}

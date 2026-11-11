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
 * same color (blue and its canary, or green), and to nothing else. Runs the scripts' own jq filters
 * on the rendered manifests. Skipped when kubectl or jq is not installed.
 */
class RolloutNetworkPoliciesTest {

    private static final List<Integer> CLUSTER_PORTS = List.of(25520, 7626);
    private static final Map<String, Object> OTHER_POD = Map.of("app", "something-else");

    @ParameterizedTest
    @ValueSource(strings = {"overlays/discovery-jdbc", "overlays/discovery-redis"})
    @SuppressWarnings("unchecked")
    void clusterPortsOpenOnlyBetweenPodsOfTheSameColor(String overlay) throws Exception {
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

        Map<String, Object> bluePod = podLabels(blue);
        Map<String, Object> greenPod = podLabels(green);
        Map<String, Object> canaryPod = podLabels(canary);
        for (Map<String, Object> pod : List.of(bluePod, greenPod, canaryPod)) {
            assertThat(policies)
                    .as("a NetworkPolicy restricting ingress and egress selects %s", pod)
                    .anySatisfy(policy -> {
                        assertThat(selects(get(policy, "spec", "podSelector"), pod))
                                .isTrue();
                        assertThat((List<String>) get(policy, "spec", "policyTypes"))
                                .contains("Ingress", "Egress");
                    });
        }
        for (int port : CLUSTER_PORTS) {
            assertThat(reaches(policies, bluePod, canaryPod, port))
                    .as("blue -> canary on %d", port)
                    .isTrue();
            assertThat(reaches(policies, canaryPod, bluePod, port))
                    .as("canary -> blue on %d", port)
                    .isTrue();
            assertThat(reaches(policies, greenPod, greenPod, port))
                    .as("green -> green on %d", port)
                    .isTrue();
            for (Map<String, Object> bluish : List.of(bluePod, canaryPod)) {
                assertThat(reaches(policies, greenPod, bluish, port))
                        .as("green -> %s on %d", bluish, port)
                        .isFalse();
                assertThat(reaches(policies, bluish, greenPod, port))
                        .as("%s -> green on %d", bluish, port)
                        .isFalse();
            }
            for (Map<String, Object> pod : List.of(bluePod, greenPod, canaryPod)) {
                assertThat(reaches(policies, OTHER_POD, pod, port))
                        .as("other pod -> %s on %d", pod, port)
                        .isFalse();
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> podLabels(Map<String, Object> deployment) {
        return (Map<String, Object>) get(deployment, "spec", "template", "metadata", "labels");
    }

    /**
     * Whether {@code from} may open a connection to {@code to} on {@code port}: egress allowed by the
     * policies selecting {@code from} and ingress allowed by those selecting {@code to}. All pods are
     * in one namespace, and a namespace selector or IP block counts as allowing (the worst case).
     */
    private static boolean reaches(
            List<Map<String, Object>> policies, Map<String, Object> from, Map<String, Object> to, int port) {
        return allows(policies, from, "Egress", "egress", "to", to, port)
                && allows(policies, to, "Ingress", "ingress", "from", from, port);
    }

    @SuppressWarnings("unchecked")
    private static boolean allows(
            List<Map<String, Object>> policies,
            Map<String, Object> pod,
            String type,
            String rulesKey,
            String peersKey,
            Map<String, Object> peer,
            int port) {
        List<Map<String, Object>> selecting = policies.stream()
                .filter(policy -> selects(get(policy, "spec", "podSelector"), pod))
                .filter(policy -> ((List<String>) get(policy, "spec", "policyTypes")).contains(type))
                .toList();
        if (selecting.isEmpty()) {
            return true; // no policy isolates the pod in this direction
        }
        return selecting.stream()
                .flatMap(policy -> ((List<Map<String, Object>>)
                                ((Map<String, Object>) policy.get("spec")).getOrDefault(rulesKey, List.of()))
                        .stream())
                .anyMatch(rule -> opensPort(rule, port) && admits(rule.get(peersKey), peer));
    }

    @SuppressWarnings("unchecked")
    private static boolean opensPort(Map<String, Object> rule, int port) {
        List<Map<String, Object>> ports = (List<Map<String, Object>>) rule.get("ports");
        return ports == null
                || ports.isEmpty()
                || ports.stream().anyMatch(p -> Integer.valueOf(port).equals(p.get("port")));
    }

    @SuppressWarnings("unchecked")
    private static boolean admits(Object peers, Map<String, Object> pod) {
        if (peers == null || ((List<Object>) peers).isEmpty()) {
            return true; // no peers listed: any source or destination
        }
        return ((List<Map<String, Object>>) peers)
                .stream().anyMatch(peer -> !peer.containsKey("podSelector") || selects(peer.get("podSelector"), pod));
    }

    @SuppressWarnings("unchecked")
    private static boolean selects(Object selector, Map<String, Object> labels) {
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

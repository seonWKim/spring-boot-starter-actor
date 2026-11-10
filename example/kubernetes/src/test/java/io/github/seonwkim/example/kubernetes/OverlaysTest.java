package io.github.seonwkim.example.kubernetes;

import static io.github.seonwkim.example.kubernetes.Kustomize.applicationYaml;
import static io.github.seonwkim.example.kubernetes.Kustomize.get;
import static io.github.seonwkim.example.kubernetes.Kustomize.render;
import static io.github.seonwkim.example.kubernetes.Kustomize.resource;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Every kustomization renders, and the settings that must agree across files do: ports, the
 * grace period against the shutdown it has to cover, and where the Cluster Bootstrap settings
 * live. Skipped when kubectl is not installed.
 */
class OverlaysTest {

    /** The directory and the name suffix it adds. */
    static Stream<Arguments> all() {
        return Stream.of(
                Arguments.of("base", ""),
                Arguments.of("overlays/local", ""),
                Arguments.of("overlays/dev", "-dev"),
                Arguments.of("overlays/prod", "-prod"),
                Arguments.of("overlays/discovery-jdbc", ""),
                Arguments.of("overlays/discovery-redis", ""));
    }

    /** The ones that find peers through the Kubernetes API. */
    static Stream<Arguments> kubernetesApi() {
        return all().filter(args -> !String.valueOf(args.get()[0]).contains("discovery-"));
    }

    private static Map<String, Object> container(List<Map<String, Object>> resources, String suffix) {
        @SuppressWarnings("unchecked")
        Map<String, Object> container = (Map<String, Object>) get(
                resource(resources, "Deployment", "spring-actor" + suffix),
                "spec",
                "template",
                "spec",
                "containers",
                0);
        return container;
    }

    @SuppressWarnings("unchecked")
    private static Object port(Object ports, String name, String key) {
        return ((List<Map<String, Object>>) ports)
                .stream()
                        .filter(port -> name.equals(port.get("name")))
                        .findFirst()
                        .orElseThrow(() -> new AssertionError("no port " + name))
                        .get(key);
    }

    @ParameterizedTest
    @MethodSource("all")
    @SuppressWarnings("unchecked")
    void configMapPodAndHeadlessServiceUseTheSamePorts(String dir, String suffix) throws Exception {
        List<Map<String, Object>> resources = render(dir);
        Map<String, Object> actor =
                (Map<String, Object>) get(applicationYaml(resource(resources, "ConfigMap", "spring-actor-config" + suffix)),
                        "spring", "actor");
        Object containerPorts = container(resources, suffix).get("ports");
        Object servicePorts = get(resource(resources, "Service", "spring-actor" + suffix), "spec", "ports");

        Object remoting = get(actor, "remote", "port");
        assertThat(get(actor, "pekko", "remote", "artery", "bind", "port")).isEqualTo(remoting);
        assertThat(port(containerPorts, "remoting", "containerPort")).isEqualTo(remoting);
        assertThat(port(servicePorts, "remoting", "targetPort")).isEqualTo(remoting);

        Object management = get(actor, "pekko", "management", "http", "port");
        assertThat(get(actor, "pekko", "management", "http", "bind-port")).isEqualTo(management);
        assertThat(port(containerPorts, "management", "containerPort")).isEqualTo(management);
        assertThat(port(servicePorts, "management", "targetPort")).isEqualTo(management);
    }

    @ParameterizedTest
    @MethodSource("all")
    @SuppressWarnings("unchecked")
    void gracePeriodLeavesTimeToDrainHttpAfterPreStopAndActorShutdown(String dir, String suffix) throws Exception {
        List<Map<String, Object>> resources = render(dir);
        Map<String, Object> actor = (Map<String, Object>)
                get(applicationYaml(resource(resources, "ConfigMap", "spring-actor-config" + suffix)), "spring", "actor");
        int grace = (Integer) get(
                resource(resources, "Deployment", "spring-actor" + suffix),
                "spec",
                "template",
                "spec",
                "terminationGracePeriodSeconds");
        List<String> preStop = (List<String>) get(container(resources, suffix), "lifecycle", "preStop", "exec", "command");
        int preStopSeconds = Integer.parseInt(preStop.get(preStop.size() - 1).replace("sleep", "").trim());
        Object timeout = get(actor, "shutdown", "timeout");
        long shutdownSeconds = timeout == null
                ? 20 // spring.actor.shutdown.timeout's default
                : Duration.parse("PT" + timeout.toString().toUpperCase()).toSeconds();

        // Spring Boot drains HTTP requests before the actor system shuts down: leave it 10s.
        assertThat(grace - preStopSeconds - shutdownSeconds).isGreaterThanOrEqualTo(10);
    }

    @ParameterizedTest
    @MethodSource("kubernetesApi")
    void clusterBootstrapSettingsReachPekkoManagement(String dir, String suffix) throws Exception {
        Map<String, Object> config =
                applicationYaml(resource(render(dir), "ConfigMap", "spring-actor-config" + suffix));

        assertThat(get(
                        config,
                        "spring",
                        "actor",
                        "pekko",
                        "management",
                        "cluster",
                        "bootstrap",
                        "contact-point-discovery",
                        "discovery-method"))
                .isEqualTo("kubernetes-api");
    }

    @ParameterizedTest
    @MethodSource("all")
    @SuppressWarnings("unchecked")
    void noJvmFlagSetsARawPekkoKey(String dir, String suffix) throws Exception {
        // A -Dpekko.* flag loses to the same key under spring.actor.pekko.* in the ConfigMap, without
        // a warning. -Dspring.actor.pekko.* overrides the ConfigMap.
        List<Map<String, Object>> env = (List<Map<String, Object>>) container(render(dir), suffix).get("env");

        assertThat(env)
                .filteredOn(variable -> "JAVA_OPTS".equals(variable.get("name")))
                .allSatisfy(variable -> assertThat(String.valueOf(variable.get("value")))
                        .doesNotContain("-Dpekko."));
    }
}

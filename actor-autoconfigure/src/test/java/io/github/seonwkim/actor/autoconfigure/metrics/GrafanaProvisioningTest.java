package io.github.seonwkim.actor.autoconfigure.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/**
 * The Grafana provisioning files in {@code monitoring/} load the Actors dashboard at startup and give it the
 * data source it queries, inside the stack of {@code monitoring/docker-compose.yml}.
 */
class GrafanaProvisioningTest {

    private static final Path MONITORING =
            Path.of("..").toAbsolutePath().normalize().resolve("monitoring");
    private static final Path DASHBOARD = MONITORING.resolve("grafana/actor-dashboard.json");
    private static final Path DATASOURCES = MONITORING.resolve("grafana/provisioning/datasources/prometheus.yaml");
    private static final Path DASHBOARD_PROVIDERS = MONITORING.resolve("grafana/provisioning/dashboards/actor.yaml");
    private static final Path COMPOSE = MONITORING.resolve("docker-compose.yml");

    private static final String DATASOURCE_VARIABLE = "${datasource}";

    @Test
    @SuppressWarnings("unchecked")
    void theDashboardProviderReadsTheDirectoryComposeMountsTheDashboardInto() throws IOException {
        Map<String, Object> grafana = composeService("grafana");
        String dashboardMount = ((List<String>) grafana.get("volumes"))
                .stream()
                        .filter(volume -> volume.startsWith("./grafana/actor-dashboard.json:"))
                        .findFirst()
                        .orElseThrow(
                                () -> new AssertionError("docker-compose.yml does not mount actor-dashboard.json"));
        String mountedAt = dashboardMount.split(":")[1];

        List<Map<String, Object>> providers =
                (List<Map<String, Object>>) yaml(DASHBOARD_PROVIDERS).get("providers");
        assertThat(providers)
                .extracting(provider -> ((Map<String, Object>) provider.get("options")).get("path"))
                .contains(Path.of(mountedAt).getParent().toString());
    }

    @Test
    void everyDataSourceTheDashboardQueriesIsTheProvisionedOne() throws IOException {
        Map<String, Object> datasource = provisionedDatasource();
        JsonNode dashboard = new ObjectMapper().readTree(DASHBOARD.toFile());

        Set<String> uids = new TreeSet<>();
        dashboard.findValues("datasource").stream()
                .filter(JsonNode::isObject)
                .forEach(ref -> uids.add(ref.path("uid").asText()));
        assertThat(uids).isNotEmpty().isSubsetOf(DATASOURCE_VARIABLE, (String) datasource.get("uid"));

        // ${datasource} starts on the default data source of the variable's type.
        JsonNode variable = templateVariable(dashboard, "datasource");
        assertThat(variable.path("query").asText()).isEqualTo(datasource.get("type"));
        assertThat(datasource.get("isDefault")).isEqualTo(true);
    }

    @Test
    void theProvisionedDataSourcePointsAtTheComposePrometheus() throws IOException {
        URI url = URI.create((String) provisionedDatasource().get("url"));

        assertThat(composeServices()).containsKey(url.getHost());
        assertThat((List<?>) composeService(url.getHost()).get("ports"))
                .anySatisfy(port -> assertThat(port.toString()).endsWith(":" + url.getPort()));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> provisionedDatasource() throws IOException {
        List<Map<String, Object>> datasources =
                (List<Map<String, Object>>) yaml(DATASOURCES).get("datasources");
        assertThat(datasources).hasSize(1);
        return datasources.get(0);
    }

    private static JsonNode templateVariable(JsonNode dashboard, String name) {
        for (JsonNode variable : dashboard.path("templating").path("list")) {
            if (name.equals(variable.path("name").asText())) {
                return variable;
            }
        }
        throw new AssertionError("The dashboard has no template variable " + name);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> composeServices() throws IOException {
        return (Map<String, Object>) yaml(COMPOSE).get("services");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> composeService(String name) throws IOException {
        Map<String, Object> service = (Map<String, Object>) composeServices().get(name);
        assertThat(service).as("docker-compose.yml service " + name).isNotNull();
        return service;
    }

    private static Map<String, Object> yaml(Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file)) {
            return new Yaml().load(in);
        }
    }
}

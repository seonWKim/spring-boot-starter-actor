package io.github.seonwkim.actor.autoconfigure.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/**
 * Every metric the Grafana dashboard and the Prometheus alert rules in {@code monitoring/} query is
 * a meter the docs describe, under the name Micrometer's Prometheus registry gives it.
 */
class MonitoringFilesTest {

    private static final Path ROOT = Path.of("..").toAbsolutePath().normalize();
    private static final Path DASHBOARD = ROOT.resolve("monitoring/grafana/actor-dashboard.json");
    private static final Path ALERTS = ROOT.resolve("monitoring/prometheus/actor-alerts.yaml");
    private static final List<Path> METER_DOCS = List.of(
            ROOT.resolve("mkdocs/docs/examples/monitoring.md"), ROOT.resolve("mkdocs/docs/guides/production.md"));

    /** A docs table row: the meter in backticks, then its type ("Counter", "gauge, tag `type`", ...). */
    private static final Pattern METER_ROW =
            Pattern.compile("^\\|\\s*`(actor\\.[a-z.]+)`\\s*\\|\\s*([A-Za-z]+)", Pattern.MULTILINE);

    /** Label matchers and grouping clauses: their identifiers are labels, not metrics. */
    private static final Pattern LABELS =
            Pattern.compile("\\{[^}]*}|\\b(by|without|on|ignoring|group_left|group_right)\\s*\\([^)]*\\)");

    private static final Pattern METRIC = Pattern.compile("\\bactor_[a-z0-9_]+");

    @Test
    void theDashboardQueriesOnlyDocumentedMeters() throws IOException {
        List<String> queries = dashboardQueries();

        assertThat(queries).isNotEmpty();
        assertThat(metricsIn(queries)).isNotEmpty().isSubsetOf(documentedPrometheusNames());
    }

    @Test
    void theAlertRulesQueryOnlyDocumentedMeters() throws IOException {
        List<String> queries = alertQueries();

        assertThat(queries).isNotEmpty();
        assertThat(metricsIn(queries)).isNotEmpty().isSubsetOf(documentedPrometheusNames());
    }

    @Test
    void metricsInLabelMatchersAreNotMistakenForLabels() {
        assertThat(metricsIn(
                        List.of("sum by (actor_class) (rate(actor_message_processed_total{actor_class=~\"$c\"}[5m]))")))
                .containsExactly("actor_message_processed_total");
    }

    /** The Prometheus names of every documented meter: Micrometer's names per meter type. */
    private static Set<String> documentedPrometheusNames() throws IOException {
        Set<String> names = new TreeSet<>();
        for (Path doc : METER_DOCS) {
            Matcher row = METER_ROW.matcher(Files.readString(doc));
            while (row.find()) {
                String base = row.group(1).replace('.', '_');
                switch (row.group(2).toLowerCase()) {
                    case "counter" -> names.add(base + "_total");
                    case "gauge" -> names.add(base);
                    case "timer" -> List.of("_count", "_sum", "_max", "_bucket")
                            .forEach(suffix -> names.add(base + "_seconds" + suffix));
                    default -> throw new AssertionError("Unknown meter type in " + doc + ": " + row.group());
                }
            }
        }
        assertThat(names).as("documented meters").isNotEmpty();
        return names;
    }

    private static Set<String> metricsIn(List<String> queries) {
        Set<String> metrics = new HashSet<>();
        for (String query : queries) {
            Matcher metric = METRIC.matcher(LABELS.matcher(query).replaceAll(" "));
            while (metric.find()) {
                metrics.add(metric.group());
            }
        }
        return metrics;
    }

    /** Panel targets and template variable queries. */
    private static List<String> dashboardQueries() throws IOException {
        JsonNode dashboard = new ObjectMapper().readTree(DASHBOARD.toFile());
        List<String> queries = new ArrayList<>();
        dashboard.findValues("expr").forEach(expr -> queries.add(expr.asText()));
        for (JsonNode variable : dashboard.path("templating").path("list")) {
            if (variable.path("query").has("query")) {
                queries.add(variable.path("query").path("query").asText());
            }
        }
        return queries;
    }

    @SuppressWarnings("unchecked")
    private static List<String> alertQueries() throws IOException {
        Map<String, Object> rule;
        try (InputStream in = Files.newInputStream(ALERTS)) {
            rule = new Yaml().load(in);
        }
        List<String> queries = new ArrayList<>();
        Map<String, Object> spec = (Map<String, Object>) rule.get("spec");
        for (Map<String, Object> group : (List<Map<String, Object>>) spec.get("groups")) {
            for (Map<String, Object> alert : (List<Map<String, Object>>) group.get("rules")) {
                queries.add((String) alert.get("expr"));
            }
        }
        return queries;
    }
}

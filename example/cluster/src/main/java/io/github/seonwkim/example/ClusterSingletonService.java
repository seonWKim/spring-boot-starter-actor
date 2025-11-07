package io.github.seonwkim.example;

import io.github.seonwkim.core.SpringActorRef;
import io.github.seonwkim.core.SpringActorSystem;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import javax.annotation.PostConstruct;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * Service that demonstrates interaction with a cluster singleton actor.
 *
 * <p>Key patterns for working with cluster singletons:
 * <ul>
 *   <li>Initialize the singleton once at startup using {@code @PostConstruct}
 *   <li>Cache the proxy reference (it's lightweight and valid for the entire cluster lifecycle)
 *   <li>All messages are automatically routed to whichever node hosts the singleton
 *   <li>The proxy handles location transparency and failover automatically
 * </ul>
 *
 * <p>This service provides methods to:
 * <ul>
 *   <li>Record metrics from the current node
 *   <li>Retrieve all aggregated metrics
 *   <li>Query metrics for a specific node
 *   <li>Reset all metrics
 * </ul>
 */
@Service
public class ClusterSingletonService {

    private final SpringActorSystem springActorSystem;
    private SpringActorRef<ClusterMetricsAggregator.Command> metricsAggregator;

    /**
     * Creates a new ClusterSingletonService with the given actor system.
     *
     * @param springActorSystem The Spring actor system
     */
    public ClusterSingletonService(SpringActorSystem springActorSystem) {
        this.springActorSystem = springActorSystem;
    }

    /**
     * Initializes the cluster singleton actor at startup.
     *
     * <p>Important notes:
     * <ul>
     *   <li>The singleton is spawned as a cluster singleton (one instance across the cluster)
     *   <li>The returned reference is a proxy that routes messages to the actual singleton
     *   <li>Calling spawn multiple times is safe (idempotent) - it returns the same proxy
     *   <li>The proxy is valid for the entire cluster lifecycle
     * </ul>
     */
    @PostConstruct
    public void init() {
        try {
            metricsAggregator = springActorSystem
                    .actor(ClusterMetricsAggregator.class)
                    .withId("metrics-aggregator")
                    .asClusterSingleton() // Make it a cluster singleton
                    .spawn()
                    .toCompletableFuture()
                    .get();

            System.out.println("Cluster singleton metrics aggregator initialized successfully");
        } catch (Exception e) {
            System.err.println("Failed to initialize cluster singleton: " + e.getMessage());
            throw new RuntimeException("Failed to initialize cluster singleton", e);
        }
    }

    /**
     * Records a metric from the current node to the cluster singleton.
     *
     * <p>The metric is sent to the singleton regardless of which node it's running on.
     * The cluster singleton proxy handles message routing automatically.
     *
     * @param metricName The name of the metric
     * @param value The value of the metric
     * @return A Mono indicating completion
     */
    public Mono<Void> recordMetric(String metricName, long value) {
        String nodeAddress = springActorSystem.getRaw().address().toString();
        long timestamp = System.currentTimeMillis();

        ClusterMetricsAggregator.RecordMetric message =
                new ClusterMetricsAggregator.RecordMetric(nodeAddress, metricName, value, timestamp);

        // Fire and forget - just tell the singleton about the metric
        metricsAggregator.tell(message);

        return Mono.empty();
    }

    /**
     * Retrieves all aggregated metrics from all nodes.
     *
     * <p>Queries the cluster singleton and returns the aggregated metrics along with
     * information about which node is currently hosting the singleton.
     *
     * @return A Mono containing the metrics response
     */
    public Mono<ClusterMetricsAggregator.MetricsResponse> getAllMetrics() {
        CompletionStage<ClusterMetricsAggregator.MetricsResponse> response = metricsAggregator
                .ask(new ClusterMetricsAggregator.GetMetrics())
                .withTimeout(Duration.ofSeconds(3))
                .onTimeout(() -> {
                    // Return empty metrics on timeout
                    return new ClusterMetricsAggregator.MetricsResponse(Map.of(), "timeout");
                })
                .execute();

        return Mono.fromCompletionStage(response);
    }

    /**
     * Retrieves metrics for a specific node.
     *
     * @param nodeAddress The address of the node to query
     * @return A Mono containing the metrics for that node
     */
    public Mono<ClusterMetricsAggregator.MetricsResponse> getNodeMetrics(String nodeAddress) {
        CompletionStage<ClusterMetricsAggregator.MetricsResponse> response = metricsAggregator
                .ask(new ClusterMetricsAggregator.GetNodeMetrics(nodeAddress))
                .withTimeout(Duration.ofSeconds(3))
                .onTimeout(() -> new ClusterMetricsAggregator.MetricsResponse(Map.of(), "timeout"))
                .execute();

        return Mono.fromCompletionStage(response);
    }

    /**
     * Resets all metrics in the cluster singleton.
     *
     * @return A Mono containing a confirmation message
     */
    public Mono<String> resetMetrics() {
        CompletionStage<String> response = metricsAggregator
                .ask(new ClusterMetricsAggregator.ResetMetrics())
                .withTimeout(Duration.ofSeconds(3))
                .onTimeout(() -> "Reset request timed out")
                .execute();

        return Mono.fromCompletionStage(response);
    }

    /**
     * Gets the current node's address (for display purposes).
     *
     * @return The current node's address
     */
    public String getCurrentNodeAddress() {
        return springActorSystem.getRaw().address().toString();
    }
}

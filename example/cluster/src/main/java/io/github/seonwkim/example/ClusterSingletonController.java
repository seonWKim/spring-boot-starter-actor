package io.github.seonwkim.example;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * REST controller that exposes cluster singleton functionality via HTTP endpoints.
 *
 * <p>This controller demonstrates how to interact with a cluster singleton from a REST API:
 * <ul>
 *   <li>POST /api/singleton/metrics - Record a metric from the current node
 *   <li>GET /api/singleton/metrics - Get all aggregated metrics
 *   <li>GET /api/singleton/metrics/node - Get metrics for a specific node
 *   <li>DELETE /api/singleton/metrics - Reset all metrics
 *   <li>GET /api/singleton/info - Get information about the current node and singleton
 * </ul>
 *
 * <p>All endpoints can be called from any node in the cluster, and they will interact
 * with the same singleton instance regardless of which node hosts it.
 */
@RestController
@RequestMapping("/api/singleton")
public class ClusterSingletonController {

    private final ClusterSingletonService singletonService;

    /**
     * Creates a new ClusterSingletonController with the given service.
     *
     * @param singletonService The service for interacting with the cluster singleton
     */
    public ClusterSingletonController(ClusterSingletonService singletonService) {
        this.singletonService = singletonService;
    }

    /**
     * Records a metric from the current node to the cluster singleton.
     *
     * <p>Example:
     * <pre>
     * POST /api/singleton/metrics?name=request_count&value=100
     * </pre>
     *
     * @param metricName The name of the metric
     * @param value The value of the metric
     * @return A confirmation message
     */
    @PostMapping("/metrics")
    public Mono<String> recordMetric(@RequestParam("name") String metricName, @RequestParam("value") long value) {

        return singletonService
                .recordMetric(metricName, value)
                .thenReturn(String.format(
                        "Metric [%s=%d] recorded from node [%s]",
                        metricName, value, singletonService.getCurrentNodeAddress()));
    }

    /**
     * Retrieves all aggregated metrics from all nodes.
     *
     * <p>Example:
     * <pre>
     * GET /api/singleton/metrics
     * </pre>
     *
     * <p>Response includes:
     * <ul>
     *   <li>All metrics from all nodes
     *   <li>The address of the node hosting the singleton
     * </ul>
     *
     * @return A Mono containing the metrics response
     */
    @GetMapping("/metrics")
    public Mono<ClusterMetricsAggregator.MetricsResponse> getAllMetrics() {
        return singletonService.getAllMetrics();
    }

    /**
     * Retrieves metrics for a specific node.
     *
     * <p>Example:
     * <pre>
     * GET /api/singleton/metrics/node?address=pekko://spring-pekko-example@127.0.0.1:2551
     * </pre>
     *
     * @param nodeAddress The address of the node to query
     * @return A Mono containing the metrics for that node
     */
    @GetMapping("/metrics/node")
    public Mono<ClusterMetricsAggregator.MetricsResponse> getNodeMetrics(@RequestParam("address") String nodeAddress) {
        return singletonService.getNodeMetrics(nodeAddress);
    }

    /**
     * Resets all metrics in the cluster singleton.
     *
     * <p>Example:
     * <pre>
     * DELETE /api/singleton/metrics
     * </pre>
     *
     * @return A Mono containing a confirmation message
     */
    @DeleteMapping("/metrics")
    public Mono<String> resetMetrics() {
        return singletonService.resetMetrics();
    }

    /**
     * Gets information about the current node.
     *
     * <p>Example:
     * <pre>
     * GET /api/singleton/info
     * </pre>
     *
     * <p>This is useful for demonstrating that requests can be sent to any node
     * in the cluster, but they all interact with the same singleton instance.
     *
     * @return Information about the current node
     */
    @GetMapping("/info")
    public Mono<NodeInfo> getInfo() {
        return Mono.just(new NodeInfo(
                singletonService.getCurrentNodeAddress(),
                "This node can interact with the cluster singleton running anywhere in the cluster"));
    }

    /**
     * Information about the current node.
     */
    public static class NodeInfo {
        public final String currentNode;
        public final String message;

        public NodeInfo(String currentNode, String message) {
            this.currentNode = currentNode;
            this.message = message;
        }
    }
}

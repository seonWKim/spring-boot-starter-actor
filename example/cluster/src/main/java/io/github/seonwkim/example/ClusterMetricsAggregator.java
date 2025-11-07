package io.github.seonwkim.example;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.seonwkim.core.AskCommand;
import io.github.seonwkim.core.SpringActorBehavior;
import io.github.seonwkim.core.SpringActorContext;
import io.github.seonwkim.core.SpringActorWithContext;
import io.github.seonwkim.core.serialization.JsonSerializable;
import java.util.HashMap;
import java.util.Map;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.springframework.stereotype.Component;

/**
 * Cluster singleton actor that aggregates metrics from all nodes in the cluster.
 *
 * <p>This actor demonstrates a practical use case for cluster singletons:
 * <ul>
 *   <li>Single point of coordination for collecting metrics across the cluster
 *   <li>All nodes can send metrics to this singleton
 *   <li>Only one instance runs in the entire cluster
 *   <li>Automatically migrates to another node if the current node fails
 *   <li>All messages are routed through a proxy for location transparency
 * </ul>
 *
 * <p>Example use cases:
 * <ul>
 *   <li>Centralized metrics collection and aggregation
 *   <li>Global cluster state monitoring
 *   <li>Cross-node statistics gathering
 *   <li>Leader election for coordination tasks
 * </ul>
 */
@Component
public class ClusterMetricsAggregator
        implements SpringActorWithContext<ClusterMetricsAggregator.Command, SpringActorContext> {

    /** Base interface for all commands that can be sent to the metrics aggregator. */
    public interface Command extends JsonSerializable {}

    /** Command to record a metric from a node. */
    public static class RecordMetric implements Command {
        public final String nodeAddress;
        public final String metricName;
        public final long value;
        public final long timestamp;

        @JsonCreator
        public RecordMetric(
                @JsonProperty("nodeAddress") String nodeAddress,
                @JsonProperty("metricName") String metricName,
                @JsonProperty("value") long value,
                @JsonProperty("timestamp") long timestamp) {
            this.nodeAddress = nodeAddress;
            this.metricName = metricName;
            this.value = value;
            this.timestamp = timestamp;
        }
    }

    /** Command to get all aggregated metrics. */
    public static class GetMetrics extends AskCommand<MetricsResponse> implements Command {
        public GetMetrics() {}
    }

    /** Command to get metrics for a specific node. */
    public static class GetNodeMetrics extends AskCommand<MetricsResponse> implements Command {
        public final String nodeAddress;

        @JsonCreator
        public GetNodeMetrics(@JsonProperty("nodeAddress") String nodeAddress) {
            this.nodeAddress = nodeAddress;
        }
    }

    /** Command to reset all metrics. */
    public static class ResetMetrics extends AskCommand<String> implements Command {
        public ResetMetrics() {}
    }

    /** Response containing aggregated metrics. */
    public static class MetricsResponse implements JsonSerializable {
        public final Map<String, Map<String, MetricData>> metrics;
        public final String singletonNodeAddress;

        @JsonCreator
        public MetricsResponse(
                @JsonProperty("metrics") Map<String, Map<String, MetricData>> metrics,
                @JsonProperty("singletonNodeAddress") String singletonNodeAddress) {
            this.metrics = metrics;
            this.singletonNodeAddress = singletonNodeAddress;
        }
    }

    /** Data for a single metric. */
    public static class MetricData implements JsonSerializable {
        public final long value;
        public final long timestamp;

        @JsonCreator
        public MetricData(@JsonProperty("value") long value, @JsonProperty("timestamp") long timestamp) {
            this.value = value;
            this.timestamp = timestamp;
        }
    }

    @Override
    public SpringActorBehavior<Command> create(SpringActorContext actorContext) {
        return SpringActorBehavior.builder(Command.class, actorContext)
                .withState(MetricsAggregatorBehavior::new)
                .onMessage(RecordMetric.class, MetricsAggregatorBehavior::onRecordMetric)
                .onMessage(GetMetrics.class, MetricsAggregatorBehavior::onGetMetrics)
                .onMessage(GetNodeMetrics.class, MetricsAggregatorBehavior::onGetNodeMetrics)
                .onMessage(ResetMetrics.class, MetricsAggregatorBehavior::onResetMetrics)
                .build();
    }

    /**
     * Behavior handler for the metrics aggregator. Maintains the state of all metrics
     * across the cluster.
     */
    private static class MetricsAggregatorBehavior {
        private final ActorContext<Command> ctx;
        // Map: nodeAddress -> (metricName -> MetricData)
        private final Map<String, Map<String, MetricData>> metrics;

        MetricsAggregatorBehavior(ActorContext<Command> ctx) {
            this.ctx = ctx;
            this.metrics = new HashMap<>();

            ctx.getLog()
                    .info(
                            "Cluster Metrics Aggregator singleton started on node: {}",
                            ctx.getSystem().address());
        }

        /**
         * Handles recording a metric from a node.
         */
        private org.apache.pekko.actor.typed.Behavior<Command> onRecordMetric(RecordMetric msg) {
            ctx.getLog().debug("Recording metric [{}={}] from node [{}]", msg.metricName, msg.value, msg.nodeAddress);

            // Get or create the metrics map for this node
            Map<String, MetricData> nodeMetrics = metrics.computeIfAbsent(msg.nodeAddress, k -> new HashMap<>());

            // Store the metric
            nodeMetrics.put(msg.metricName, new MetricData(msg.value, msg.timestamp));

            return Behaviors.same();
        }

        /**
         * Handles getting all metrics from all nodes.
         */
        private org.apache.pekko.actor.typed.Behavior<Command> onGetMetrics(GetMetrics msg) {
            ctx.getLog().debug("Getting all metrics (nodes: {})", metrics.size());

            String nodeAddress = ctx.getSystem().address().toString();
            MetricsResponse response = new MetricsResponse(new HashMap<>(metrics), nodeAddress);

            msg.reply(response);
            return Behaviors.same();
        }

        /**
         * Handles getting metrics for a specific node.
         */
        private org.apache.pekko.actor.typed.Behavior<Command> onGetNodeMetrics(GetNodeMetrics msg) {
            ctx.getLog().debug("Getting metrics for node [{}]", msg.nodeAddress);

            Map<String, MetricData> nodeMetrics = metrics.get(msg.nodeAddress);
            Map<String, Map<String, MetricData>> result = new HashMap<>();

            if (nodeMetrics != null) {
                result.put(msg.nodeAddress, new HashMap<>(nodeMetrics));
            }

            String nodeAddress = ctx.getSystem().address().toString();
            MetricsResponse response = new MetricsResponse(result, nodeAddress);

            msg.reply(response);
            return Behaviors.same();
        }

        /**
         * Handles resetting all metrics.
         */
        private org.apache.pekko.actor.typed.Behavior<Command> onResetMetrics(ResetMetrics msg) {
            ctx.getLog().info("Resetting all metrics");

            int nodeCount = metrics.size();
            int totalMetrics = metrics.values().stream().mapToInt(Map::size).sum();

            metrics.clear();

            String responseMessage =
                    String.format("Reset complete. Cleared %d metrics from %d nodes.", totalMetrics, nodeCount);

            msg.reply(responseMessage);
            return Behaviors.same();
        }
    }
}

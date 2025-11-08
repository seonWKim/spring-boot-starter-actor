package io.github.seonwkim.metrics.examples;

import io.github.seonwkim.metrics.impl.ActorSystemMetricsCollector;
import io.github.seonwkim.metrics.impl.HighPriorityMetricsCollector;
import io.github.seonwkim.metrics.interceptor.ActorLifeCycleEventInterceptorsHolder;
import io.github.seonwkim.metrics.interceptor.InvokeAdviceEventInterceptorsHolder;

/**
 * Example demonstrating how to use the high priority metrics collectors.
 *
 * <p>This example shows how to:
 * <ul>
 *   <li>Register the high priority metrics collectors</li>
 *   <li>Access collected metrics</li>
 *   <li>Export metrics to monitoring systems like Prometheus</li>
 * </ul>
 *
 * <p>Note: This is a documentation example and not meant to be executed.
 * To run this, you need to:
 * <ol>
 *   <li>Start your application with the metrics agent: -javaagent:metrics-agent.jar</li>
 *   <li>Register the collectors during application startup</li>
 *   <li>Access metrics via the collector instances</li>
 * </ol>
 */
public class HighPriorityMetricsExample {

    private final HighPriorityMetricsCollector metricsCollector;
    private final ActorSystemMetricsCollector systemMetrics;

    /**
     * Initialize and register the metrics collectors.
     * This should be called during application startup.
     */
    public HighPriorityMetricsExample() {
        // Create the collectors
        metricsCollector = new HighPriorityMetricsCollector();
        systemMetrics = new ActorSystemMetricsCollector();

        // Register with the instrumentation hooks
        InvokeAdviceEventInterceptorsHolder.register(metricsCollector);
        ActorLifeCycleEventInterceptorsHolder.register(systemMetrics);
    }

    /**
     * Example of accessing actor message processing metrics.
     */
    public void printMessageProcessingMetrics(String messageType) {
        // Get processing time statistics
        HighPriorityMetricsCollector.TimerMetric processingTime =
                metricsCollector.getProcessingTimeMetric(messageType);

        if (processingTime != null) {
            System.out.println("Processing Time Metrics for " + messageType + ":");
            System.out.println("  Count: " + processingTime.getCount());
            System.out.println("  Average: " + processingTime.getAverageTimeNanos() / 1_000_000 + " ms");
            System.out.println("  Min: " + processingTime.getMinTimeNanos() / 1_000_000 + " ms");
            System.out.println("  Max: " + processingTime.getMaxTimeNanos() / 1_000_000 + " ms");
        }

        // Get time in mailbox statistics
        HighPriorityMetricsCollector.TimerMetric timeInMailbox =
                metricsCollector.getTimeInMailboxMetric(messageType);

        if (timeInMailbox != null) {
            System.out.println("Time in Mailbox Metrics for " + messageType + ":");
            System.out.println("  Average: " + timeInMailbox.getAverageTimeNanos() / 1_000_000 + " ms");
        }

        // Get message counts
        long processedCount = metricsCollector.getMessagesProcessedCount(messageType);
        long errorCount = metricsCollector.getErrorCount(messageType);

        System.out.println("Message Counts for " + messageType + ":");
        System.out.println("  Processed: " + processedCount);
        System.out.println("  Errors: " + errorCount);
    }

    /**
     * Example of accessing actor system-level metrics.
     */
    public void printSystemMetrics() {
        System.out.println("Actor System Metrics:");
        System.out.println("  Active Actors: " + systemMetrics.getActiveActors());
        System.out.println("  Total Created: " + systemMetrics.getCreatedActorsTotal());
        System.out.println("  Total Terminated: " + systemMetrics.getTerminatedActorsTotal());
    }

    /**
     * Example of exporting metrics to Prometheus/Micrometer.
     * 
     * This shows the pattern used in ActorClusterMetricsExporter.
     */
    public void exportToPrometheus() {
        // This is pseudo-code showing the integration pattern
        // In a real application, you would use Micrometer's MeterRegistry
        
        /*
        MeterRegistry registry = ...; // Get your MeterRegistry instance
        
        // Export processing time as a Timer
        for (String messageType : getTrackedMessageTypes()) {
            TimerMetric metric = metricsCollector.getProcessingTimeMetric(messageType);
            if (metric != null) {
                Timer.builder("actor.processing.time")
                    .description("Time spent processing messages")
                    .tag("messageType", messageType)
                    .register(registry);
            }
        }
        
        // Export counters
        Gauge.builder("actor.system.active", systemMetrics::getActiveActors)
            .description("Number of active actors")
            .register(registry);
            
        Counter.builder("actor.messages.processed.total")
            .description("Total messages processed")
            .register(registry);
        */
    }

    /**
     * Example of periodic metrics reporting.
     */
    public void startPeriodicReporting() {
        // In a real application, use a ScheduledExecutorService or similar
        /*
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
        scheduler.scheduleAtFixedRate(() -> {
            printSystemMetrics();
            
            // Report metrics for known message types
            printMessageProcessingMetrics("MyMessage");
            printMessageProcessingMetrics("AnotherMessage");
        }, 0, 60, TimeUnit.SECONDS); // Report every minute
        */
    }

    /**
     * Clean up and unregister metrics collectors.
     * Call this during application shutdown.
     */
    public void shutdown() {
        InvokeAdviceEventInterceptorsHolder.unregister(metricsCollector);
        ActorLifeCycleEventInterceptorsHolder.unregister(systemMetrics);
    }

    /**
     * Main method demonstrating the setup (for documentation purposes).
     */
    public static void main(String[] args) {
        System.out.println("High Priority Metrics Example");
        System.out.println("==============================");
        System.out.println();
        System.out.println("To use these metrics in your application:");
        System.out.println("1. Start with the metrics agent: java -javaagent:metrics-agent.jar -jar your-app.jar");
        System.out.println("2. Create and register the collectors at application startup");
        System.out.println("3. Access metrics via the collector instances");
        System.out.println("4. Export to your monitoring system (e.g., Prometheus, Grafana)");
        System.out.println();
        System.out.println("See ActorClusterMetricsExporter in the example/chat module for a complete example.");
    }
}

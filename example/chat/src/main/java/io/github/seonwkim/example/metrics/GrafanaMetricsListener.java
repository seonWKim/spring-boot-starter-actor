package io.github.seonwkim.example.metrics;

import io.github.seonwkim.metrics.ActorMetricsRegistry;
import io.github.seonwkim.metrics.ActorMetricsRegistry.ProcessingTimeStats;
import io.github.seonwkim.metrics.ActorMetricsRegistry.TimeInMailboxStats;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import javax.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Enhanced metrics exporter that integrates ActorMetricsRegistry with Micrometer.
 * 
 * This replaces the previous custom implementation with the centralized ActorMetricsRegistry
 * providing comprehensive observability for the actor system.
 */
@Component
public class GrafanaMetricsListener {
    private static final Logger logger = LoggerFactory.getLogger(GrafanaMetricsListener.class);

    private final MeterRegistry meterRegistry;
    private final ActorMetricsRegistry actorMetrics;

    public GrafanaMetricsListener(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        this.actorMetrics = ActorMetricsRegistry.getInstance();
    }

    @PostConstruct
    public void register() {
        logger.info("Registering actor system metrics with Micrometer");
        registerStaticMetrics();
    }

    private void registerStaticMetrics() {
        // Lifecycle Metrics
        Gauge.builder("actor.system.active", actorMetrics, ActorMetricsRegistry::getActiveActors)
                .description("Number of currently active actors")
                .register(meterRegistry);

        Gauge.builder("actor.system.created.total", actorMetrics, ActorMetricsRegistry::getActorsCreated)
                .description("Total actors created")
                .register(meterRegistry);

        Gauge.builder("actor.system.terminated.total", actorMetrics, ActorMetricsRegistry::getActorsTerminated)
                .description("Total actors terminated")
                .register(meterRegistry);

        Gauge.builder("actor.lifecycle.restarts.total", actorMetrics, ActorMetricsRegistry::getActorRestarts)
                .description("Total actor restarts")
                .register(meterRegistry);

        Gauge.builder("actor.lifecycle.stops.total", actorMetrics, ActorMetricsRegistry::getActorStops)
                .description("Total actor stops")
                .register(meterRegistry);

        // Message Processing Metrics
        Gauge.builder("actor.messages.processed.total", actorMetrics, ActorMetricsRegistry::getMessagesProcessed)
                .description("Total messages processed")
                .register(meterRegistry);

        // Error Metrics
        Gauge.builder("actor.errors.total", actorMetrics, ActorMetricsRegistry::getProcessingErrors)
                .description("Total processing errors")
                .register(meterRegistry);

        // System Metrics
        Gauge.builder("actor.system.dead-letters.total", actorMetrics, ActorMetricsRegistry::getDeadLetters)
                .description("Total dead letters")
                .register(meterRegistry);

        Gauge.builder(
                        "actor.system.unhandled-messages.total",
                        actorMetrics,
                        ActorMetricsRegistry::getUnhandledMessages)
                .description("Total unhandled messages")
                .register(meterRegistry);

        Gauge.builder("actor.mailbox.overflow.total", actorMetrics, ActorMetricsRegistry::getMailboxOverflows)
                .description("Total mailbox overflow events")
                .register(meterRegistry);

        logger.info("Static actor metrics registered successfully");
    }

    @Scheduled(fixedRate = 5000) // Update every 5 seconds
    public void updateDynamicMetrics() {
        try {
            // Update per-message-type counters
            actorMetrics.getAllMessagesByType().forEach((messageType, counter) -> {
                Gauge.builder("actor.messages.processed", counter, c -> c.sum())
                        .tag("message_type", messageType)
                        .description("Messages processed by type")
                        .register(meterRegistry);
            });

            // Update per-error-type counters
            actorMetrics.getAllErrorsByType().forEach((errorType, counter) -> {
                Gauge.builder("actor.errors", counter, c -> c.sum())
                        .tag("error_type", errorType)
                        .description("Errors by type")
                        .register(meterRegistry);
            });

            // Update processing time stats
            actorMetrics.getAllProcessingTimeStats().forEach((messageType, stats) -> {
                Gauge.builder(
                                "actor.processing.time.avg",
                                stats,
                                s -> s.getAverageTimeNanos() / 1_000_000.0) // Convert to ms
                        .tag("message_type", messageType)
                        .description("Average processing time in milliseconds")
                        .register(meterRegistry);

                Gauge.builder("actor.processing.time.max", stats, s -> s.getMaxTimeNanos() / 1_000_000.0)
                        .tag("message_type", messageType)
                        .description("Maximum processing time in milliseconds")
                        .register(meterRegistry);

                Gauge.builder("actor.processing.time.min", stats, s -> s.getMinTimeNanos() / 1_000_000.0)
                        .tag("message_type", messageType)
                        .description("Minimum processing time in milliseconds")
                        .register(meterRegistry);
            });

            // Update time-in-mailbox stats
            actorMetrics.getAllTimeInMailboxStats().forEach((actorPath, stats) -> {
                String sanitizedPath = sanitizeActorPath(actorPath);
                Gauge.builder("actor.time.in.mailbox.avg", stats, s -> s.getAverageTimeNanos() / 1_000_000.0)
                        .tag("actor_path", sanitizedPath)
                        .description("Average time in mailbox in milliseconds")
                        .register(meterRegistry);
            });

            // Update mailbox sizes
            actorMetrics.getAllMailboxSizes().forEach((actorPath, size) -> {
                String sanitizedPath = sanitizeActorPath(actorPath);
                Gauge.builder("actor.mailbox.size.current", size, s -> s.get())
                        .tag("actor_path", sanitizedPath)
                        .description("Current mailbox size")
                        .register(meterRegistry);
            });

            logger.debug("Dynamic metrics updated successfully");
        } catch (Exception e) {
            logger.error("Error updating dynamic metrics", e);
        }
    }

    private String sanitizeActorPath(String path) {
        // Remove actor system prefix and sanitize for Prometheus
        return path.replaceAll("pekko://[^/]+/", "").replaceAll("[^a-zA-Z0-9_/-]", "_");
    }
}

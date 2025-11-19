# Actor Metrics Integration Guide

## Overview

The metrics module now provides comprehensive observability for the actor system through the `ActorMetricsRegistry`. This guide shows how to integrate these metrics with Micrometer for export to monitoring systems like Prometheus, Grafana, etc.

## Architecture

The metrics system has three layers:

1. **ByteBuddy Instrumentation** - Intercepts actor lifecycle and message processing events at runtime
2. **ActorMetricsRegistry** - Centralized, thread-safe storage for all metrics
3. **Micrometer Integration** - Exports metrics to monitoring systems (implemented at application level)

## Available Metrics

### Actor Lifecycle Metrics
- `system.active-actors` - Current number of active actors (Gauge)
- `system.created-actors.total` - Total actors created (Counter)
- `system.terminated-actors.total` - Total actors terminated (Counter)
- `actor.lifecycle.restarts` - Actor restart count (Counter)
- `actor.lifecycle.stops` - Actor stop count (Counter)

### Message Processing Metrics
- `actor.messages.processed` - Total messages processed (Counter)
- `actor.messages.processed.by-type` - Messages processed by type (Counter with tags)
- `actor.processing-time` - Message processing time (Timer: min/max/avg)

### Error Metrics
- `actor.errors` - Total processing errors (Counter)
- `actor.errors.by-type` - Errors by exception type (Counter with tags)

### Mailbox Metrics
- `actor.mailbox.size` - Current mailbox size per actor (Gauge)
- `actor.mailbox.size.max` - Maximum mailbox size reached (Gauge)
- `actor.time-in-mailbox` - Time from enqueue to dequeue (Timer: min/max/avg)
- `actor.mailbox.overflow` - Mailbox overflow events (Counter)

### System Metrics
- `system.dead-letters` - Dead letter count (Counter) [infrastructure ready]
- `system.unhandled-messages` - Unhandled message count (Counter) [infrastructure ready]

## Integration with Micrometer

### Step 1: Add Micrometer Dependency

```kotlin
// build.gradle.kts
dependencies {
    implementation("io.micrometer:micrometer-registry-prometheus:1.12.0")
}
```

### Step 2: Create Micrometer Exporter

```java
package com.example.metrics;

import io.github.seonwkim.metrics.ActorMetricsRegistry;
import io.github.seonwkim.metrics.ActorMetricsRegistry.ProcessingTimeStats;
import io.github.seonwkim.metrics.ActorMetricsRegistry.TimeInMailboxStats;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import javax.annotation.PostConstruct;
import org.springframework.stereotype.Component;

@Component
public class ActorMetricsExporter {
    
    private final MeterRegistry meterRegistry;
    private final ActorMetricsRegistry actorMetrics;
    
    public ActorMetricsExporter(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        this.actorMetrics = ActorMetricsRegistry.getInstance();
    }
    
    @PostConstruct
    public void registerMetrics() {
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
        
        Gauge.builder("actor.system.unhandled-messages.total", actorMetrics, ActorMetricsRegistry::getUnhandledMessages)
            .description("Total unhandled messages")
            .register(meterRegistry);
        
        Gauge.builder("actor.mailbox.overflow.total", actorMetrics, ActorMetricsRegistry::getMailboxOverflows)
            .description("Total mailbox overflow events")
            .register(meterRegistry);
        
        // Register per-message-type metrics (dynamic)
        // These are updated in a scheduled task
        scheduleMetricsUpdate();
    }
    
    @Scheduled(fixedRate = 5000) // Update every 5 seconds
    public void updateDynamicMetrics() {
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
            Gauge.builder("actor.processing.time.avg", stats, 
                    s -> s.getAverageTimeNanos() / 1_000_000.0) // Convert to ms
                .tag("message_type", messageType)
                .description("Average processing time in milliseconds")
                .register(meterRegistry);
            
            Gauge.builder("actor.processing.time.max", stats,
                    s -> s.getMaxTimeNanos() / 1_000_000.0)
                .tag("message_type", messageType)
                .description("Maximum processing time in milliseconds")
                .register(meterRegistry);
        });
        
        // Update time-in-mailbox stats
        actorMetrics.getAllTimeInMailboxStats().forEach((actorPath, stats) -> {
            Gauge.builder("actor.time.in.mailbox.avg", stats,
                    s -> s.getAverageTimeNanos() / 1_000_000.0)
                .tag("actor_path", sanitizeActorPath(actorPath))
                .description("Average time in mailbox in milliseconds")
                .register(meterRegistry);
        });
        
        // Update mailbox sizes
        actorMetrics.getAllMailboxSizes().forEach((actorPath, size) -> {
            Gauge.builder("actor.mailbox.size", size, s -> s.get())
                .tag("actor_path", sanitizeActorPath(actorPath))
                .description("Current mailbox size")
                .register(meterRegistry);
        });
    }
    
    private String sanitizeActorPath(String path) {
        // Remove actor system prefix and sanitize for Prometheus
        return path.replaceAll("pekko://[^/]+/", "")
                  .replaceAll("[^a-zA-Z0-9_/-]", "_");
    }
}
```

### Step 3: Configure Application

```yaml
# application.yml
spring:
  application:
    name: actor-app

management:
  endpoints:
    web:
      exposure:
        include: health,metrics,prometheus
  endpoint:
    prometheus:
      enabled: true
    metrics:
      enabled: true
  metrics:
    export:
      prometheus:
        enabled: true
```

### Step 4: Run with Metrics Agent

```bash
java -javaagent:metrics/build/libs/metrics-{version}-agent.jar \
     -jar your-application.jar
```

## Querying Metrics

### Prometheus Queries

```promql
# Active actors
actor_system_active

# Message processing rate (messages/sec)
rate(actor_messages_processed_total[5m])

# Error rate
rate(actor_errors_total[5m])

# Average processing time by message type
actor_processing_time_avg{message_type="OrderCommand"}

# P95 processing time (if using histogram)
histogram_quantile(0.95, rate(actor_processing_time_bucket[5m]))

# Mailbox size
actor_mailbox_size{actor_path="user_order_actor"}
```

### Grafana Dashboard Example

```json
{
  "title": "Actor System Overview",
  "panels": [
    {
      "title": "Active Actors",
      "targets": [{
        "expr": "actor_system_active"
      }]
    },
    {
      "title": "Message Processing Rate",
      "targets": [{
        "expr": "rate(actor_messages_processed_total[5m])"
      }]
    },
    {
      "title": "Error Rate",
      "targets": [{
        "expr": "rate(actor_errors_total[5m])"
      }]
    }
  ]
}
```

## Best Practices

1. **Use @Scheduled for Dynamic Metrics** - Dynamic metrics (per message type, per actor) should be updated periodically
2. **Sanitize Tags** - Actor paths and class names should be sanitized for Prometheus compatibility
3. **Limit Cardinality** - Be careful with high-cardinality tags (e.g., don't use correlation IDs as tags)
4. **Aggregate When Possible** - For production, aggregate per-actor metrics to reduce cardinality
5. **Monitor Memory** - The metrics registry uses ConcurrentHashMaps; monitor memory usage in high-throughput systems

## Troubleshooting

### Metrics Not Appearing

1. Verify the javaagent is loaded:
   ```bash
   ps aux | grep javaagent
   ```

2. Check that interceptors are registered:
   ```java
   // Add debug logging
   logger.info("Registered interceptors: {}", 
       InvokeAdviceEventInterceptorsHolder.getInterceptorCount());
   ```

3. Verify metrics are being collected:
   ```java
   ActorMetricsRegistry registry = ActorMetricsRegistry.getInstance();
   logger.info("Active actors: {}", registry.getActiveActors());
   ```

### High Memory Usage

- Use `@Scheduled(fixedRate = 30000)` for less frequent updates
- Aggregate metrics at the message class level instead of per-instance
- Set retention limits for actor-specific metrics

## Next Steps

1. **Add Dispatcher Metrics** - Track thread pool utilization
2. **Add Health Checks** - Integrate with Spring Boot Actuator
3. **Add Distributed Tracing** - OpenTelemetry integration
4. **Create Grafana Dashboards** - Pre-built templates for common scenarios

## See Also

- [Micrometer Documentation](https://micrometer.io/docs)
- [Prometheus Best Practices](https://prometheus.io/docs/practices/)
- [Grafana Dashboard Examples](https://grafana.com/grafana/dashboards)

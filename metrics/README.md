# Actor Metrics Module

The Metrics module provides comprehensive observability for Pekko (Apache Pekko) actor systems through runtime instrumentation using ByteBuddy Java agent.

## Overview

This module captures detailed metrics about:
- Actor lifecycle events (creation, termination, restarts, stops)
- Message processing (throughput, latency, errors)
- Mailbox behavior (size, time-in-mailbox, overflows)
- System-level statistics (active actors, dead letters, unhandled messages)

All metrics are stored in a thread-safe, centralized `ActorMetricsRegistry` and can be exported to any monitoring system via Micrometer.

## Quick Start

### 1. Include the Java Agent

Run your application with the metrics agent:

```bash
java -javaagent:metrics/build/libs/metrics-{version}-agent.jar \
     -jar your-application.jar
```

### 2. Register Comprehensive Interceptors

```java
import io.github.seonwkim.metrics.impl.ComprehensiveInvokeAdviceInterceptor;
import io.github.seonwkim.metrics.impl.ComprehensiveLifecycleInterceptor;
import io.github.seonwkim.metrics.interceptor.ActorLifeCycleEventInterceptorsHolder;
import io.github.seonwkim.metrics.interceptor.InvokeAdviceEventInterceptorsHolder;

// Register comprehensive interceptors for full metrics collection
InvokeAdviceEventInterceptorsHolder.register(new ComprehensiveInvokeAdviceInterceptor());
ActorLifeCycleEventInterceptorsHolder.register(new ComprehensiveLifecycleInterceptor());
```

### 3. Access Metrics

```java
import io.github.seonwkim.metrics.ActorMetricsRegistry;

ActorMetricsRegistry registry = ActorMetricsRegistry.getInstance();

// Lifecycle metrics
long activeActors = registry.getActiveActors();
long actorsCreated = registry.getActorsCreated();
long actorsTerminated = registry.getActorsTerminated();

// Message processing metrics
long messagesProcessed = registry.getMessagesProcessed();
long errors = registry.getProcessingErrors();

// Get processing time stats for a specific message type
ActorMetricsRegistry.ProcessingTimeStats stats = 
    registry.getProcessingTimeStats("OrderCommand");
if (stats != null) {
    long avgTimeMs = stats.getAverageTimeNanos() / 1_000_000;
    long maxTimeMs = stats.getMaxTimeNanos() / 1_000_000;
}
```

## Available Metrics

### Actor Lifecycle Metrics
- `system.active-actors` - Number of currently active actors (Gauge)
- `system.created-actors.total` - Total actors created (Counter)
- `system.terminated-actors.total` - Total actors terminated (Counter)
- `actor.lifecycle.restarts` - Actor restart count (Counter)
- `actor.lifecycle.stops` - Actor stop count (Counter)

### Message Processing Metrics
- `actor.messages.processed` - Total messages processed (Counter)
- `actor.messages.processed.by-type` - Messages by type (Counter with tags)
- `actor.processing-time.avg/min/max` - Processing time statistics (Timer)

### Error Metrics
- `actor.errors` - Total processing errors (Counter)
- `actor.errors.by-type` - Errors by exception type (Counter with tags)

### Mailbox Metrics
- `actor.mailbox.size` - Current mailbox size per actor (Gauge)
- `actor.mailbox.size.max` - Maximum mailbox size reached (Gauge)
- `actor.time-in-mailbox` - Time from enqueue to dequeue (Timer)
- `actor.mailbox.overflow` - Mailbox overflow events (Counter)

### System Metrics
- `system.dead-letters` - Dead letter count (Counter) [infrastructure ready]
- `system.unhandled-messages` - Unhandled message count (Counter) [infrastructure ready]

## Integration with Micrometer

For complete Micrometer integration examples, see [INTEGRATION_GUIDE.md](INTEGRATION_GUIDE.md).

Quick example:

```java
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
        // Register gauges
        Gauge.builder("actor.system.active", actorMetrics, 
                ActorMetricsRegistry::getActiveActors)
            .description("Number of currently active actors")
            .register(meterRegistry);
        
        // See INTEGRATION_GUIDE.md for complete examples
    }
}
```

## Spring Boot Integration

For Spring Boot applications, create a component to auto-register interceptors:

```java
@Component
public class MetricsInterceptorRegistrar {
    
    private final ComprehensiveInvokeAdviceInterceptor invokeInterceptor;
    private final ComprehensiveLifecycleInterceptor lifecycleInterceptor;
    
    public MetricsInterceptorRegistrar() {
        this.invokeInterceptor = new ComprehensiveInvokeAdviceInterceptor();
        this.lifecycleInterceptor = new ComprehensiveLifecycleInterceptor();
    }
    
    @PostConstruct
    public void register() {
        InvokeAdviceEventInterceptorsHolder.register(invokeInterceptor);
        ActorLifeCycleEventInterceptorsHolder.register(lifecycleInterceptor);
    }
}
```

See the `example/chat` application for a complete working example.

## Architecture

The metrics system consists of three layers:

1. **ByteBuddy Instrumentation Layer**
   - `ActorInstrumentation` - Instruments ActorCell for lifecycle events
   - `EnvelopeInstrumentation` - Instruments message envelopes for timing

2. **Metrics Collection Layer**
   - `ActorMetricsRegistry` - Centralized, thread-safe metrics storage
   - `ComprehensiveInvokeAdviceInterceptor` - Captures message processing events
   - `ComprehensiveLifecycleInterceptor` - Captures actor lifecycle events

3. **Export Layer** (implemented at application level)
   - Micrometer integration for Prometheus/Grafana
   - Custom exporters for other monitoring systems

## Testing

Run the comprehensive test suite:

```bash
./gradlew :metrics:test
```

The test suite includes:
- Unit tests for `ActorMetricsRegistry` (thread-safety, correctness)
- Integration tests with real actors
- Tests for all metric types (counters, gauges, timers)

## Performance

The metrics system is designed for production use with minimal overhead:
- Thread-safe concurrent data structures (ConcurrentHashMap, LongAdder, AtomicLong)
- Efficient ByteBuddy instrumentation
- No blocking operations
- Graceful error handling (metrics failures don't affect actor processing)

Expected overhead: < 2% in high-throughput scenarios

## Documentation

- [INTEGRATION_GUIDE.md](INTEGRATION_GUIDE.md) - Complete Micrometer integration guide
- [TODO.md](TODO.md) - Full list of planned metrics (50+ metrics)
- [Roadmap](../roadmap/6-observability/) - Implementation roadmap and task breakdown

## Example Application

See `example/chat` for a complete example demonstrating:
- Metrics collection with comprehensive interceptors
- Micrometer integration with Prometheus
- Dynamic metrics updates with @Scheduled
- Grafana dashboard setup

## Future Enhancements

See [TODO.md](TODO.md) for the complete list of planned features:
- Dispatcher/thread pool metrics
- Scheduler metrics
- Remote/serialization metrics
- Cluster sharding metrics
- Health checks (Spring Boot Actuator)
- MDC logging support
- Distributed tracing (OpenTelemetry)
- Grafana dashboard templates

## Contributing

When adding new metrics:

1. Add the metric to `ActorMetricsRegistry`
2. Update the appropriate interceptor to collect the metric
3. Add tests to verify the metric is collected correctly
4. Update documentation (README.md, INTEGRATION_GUIDE.md, TODO.md)
5. Add Micrometer export example if applicable

## License

This module is part of the spring-boot-starter-actor project and is licensed under the Apache License 2.0.

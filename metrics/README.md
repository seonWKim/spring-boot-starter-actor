# Metrics Module

The Metrics module provides instrumentation for Pekko actors to collect performance metrics. It uses Java agent with ByteBuddy to intercept method calls, inspired by the Kamon project.

## Available Metrics Collectors

### HighPriorityMetricsCollector

The `HighPriorityMetricsCollector` provides essential metrics for production monitoring:

- **actor.processing-time** - Time taken to process messages (Timer)
- **actor.time-in-mailbox** - Time from message enqueue to dequeue (Timer)
- **actor.messages.processed** - Total messages processed (Counter)
- **actor.errors** - Count of processing errors (Counter)

### ActorSystemMetricsCollector

The `ActorSystemMetricsCollector` provides system-level metrics:

- **system.active-actors** - Number of active actors (Gauge)
- **system.created-actors.total** - Total actors created (Counter)
- **system.terminated-actors.total** - Total actors terminated (Counter)
- **mailbox.size.current** - Current mailbox size per actor (Gauge)

## Usage

### 1. Include the Agent

To use the metrics module, you need to include the Java agent when starting your application:

```bash
java -javaagent:metrics-{version}-agent.jar -jar your-application.jar
```

### 2. Register High Priority Metrics Collectors

Use the pre-built collectors for common metrics:

```java
import io.github.seonwkim.metrics.impl.HighPriorityMetricsCollector;
import io.github.seonwkim.metrics.impl.ActorSystemMetricsCollector;
import io.github.seonwkim.metrics.interceptor.InvokeAdviceEventInterceptorsHolder;
import io.github.seonwkim.metrics.interceptor.ActorLifeCycleEventInterceptorsHolder;

// Register high priority metrics collector
HighPriorityMetricsCollector metricsCollector = new HighPriorityMetricsCollector();
InvokeAdviceEventInterceptorsHolder.register(metricsCollector);

// Register actor system metrics collector
ActorSystemMetricsCollector systemMetrics = new ActorSystemMetricsCollector();
ActorLifeCycleEventInterceptorsHolder.register(systemMetrics);

// Access metrics
long messagesProcessed = metricsCollector.getMessagesProcessedCount("MyMessage");
long activeActors = systemMetrics.getActiveActors();
```

### 3. Implement Custom Event Interceptors

Create custom interceptors for specific needs:

```java
import io.github.seonwkim.metrics.interceptor.InvokeAdviceEventInterceptorsHolder;

// Register a custom interceptor for actor messages
InvokeAdviceEventInterceptorsHolder.register(new InvokeAdviceEventInterceptor() {
    @Override
    public void onEnter(Object envelope) {
        // Called when a message is about to be processed
    }

    @Override
    public void onExit(Object envelope, long startTime) {
        // Called when message processing is complete
        // Calculate duration: System.nanoTime() - startTime
    }
});
```

### 4. Export Metrics to Monitoring Systems

Export the collected metrics to your monitoring system. The example chat application demonstrates integration with Micrometer and Prometheus:

```java
// See ActorClusterMetricsExporter in the example chat application
```

Export the collected metrics to your monitoring system. The example chat application demonstrates integration with Micrometer and Prometheus:

```java
// See ActorClusterMetricsExporter in the example chat application
```

## Integration with Monitoring

This module is designed to work with the monitoring setup provided in the `scripts/monitoring` directory, which includes:

- Prometheus for metrics collection
- Grafana for metrics visualization

See the main README.md for instructions on how to start the monitoring stack.

## Example

For a complete example of how to use this module, see the `ActorClusterMetricsExporter` class in the example chat application.

# Metrics Module

The Metrics module provides instrumentation for Pekko actors to collect performance metrics. It uses Java agent to intercept method calls.

## Usage

### 1. Include the Agent

To use the metrics module, you need to include the Java agent when starting your application:

```bash
java -javaagent:metrics-{version}-agent.jar -jar your-application.jar
```

### 2. Implement Event Listeners

Create listeners to process the metrics events:

```java
import io.github.seonwkim.metrics.ActorInstrumentationEventListener;
import io.github.seonwkim.metrics.ActorInstrumentationEventListener.InvokeAdviceEventListener;

// Register a listener for regular actor messages
ActorInstrumentationEventListener.register(new InvokeAdviceEventListener() {
    @Override
    public void onEnter(Envelope envelope) {
        // Called when a message is about to be processed
    }

    @Override
    public void onExit(Envelope envelope, long startTime, Throwable throwable) {
        // Called when message processing is complete
        // Calculate duration: System.nanoTime() - startTime
    }
});
```

### 3. Export Metrics

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

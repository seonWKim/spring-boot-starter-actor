# metrics-micrometer

Micrometer integration for spring-boot-starter-actor metrics.

This module provides a lightweight adapter that bridges the actor metrics API to Micrometer's `MeterRegistry`, enabling integration with monitoring systems like Prometheus, Grafana, DataDog, etc.

## Features

- **Zero Framework Dependencies**: Pure Micrometer integration with no Spring or other framework dependencies
- **Manual Configuration**: Users have full control over metrics setup
- **Compatible with Any Micrometer Backend**: Works with all Micrometer registry implementations

## Installation

Add the dependency to your project:

**Gradle:**
```gradle
dependencies {
    implementation 'io.github.seonwkim:spring-boot-starter-actor-metrics-micrometer:0.6.3'
}
```

**Maven:**
```xml
<dependency>
    <groupId>io.github.seonwkim</groupId>
    <artifactId>spring-boot-starter-actor-metrics-micrometer</artifactId>
    <version>0.6.3</version>
</dependency>
```

## Usage

### Spring Boot Example

Create a configuration class to wire up the metrics:

```java
@Configuration
public class ActorMetricsConfiguration {

    @Bean
    public MetricsRegistry actorMetricsRegistry(MeterRegistry meterRegistry) {
        // Create Micrometer-based backend
        MetricsBackend backend = new MicrometerMetricsBackend(meterRegistry);

        // Build configuration with common tags
        MetricsConfiguration config = MetricsConfiguration.builder()
            .enabled(true)
            .tag("application", "my-app")
            .tag("environment", "production")
            .build();

        // Create metrics registry
        MetricsRegistry metricsRegistry = MetricsRegistry.builder()
            .configuration(config)
            .backend(backend)
            .build();

        // Discover and register instrumentation modules via SPI
        ServiceLoader<InstrumentationModule> moduleLoader = ServiceLoader.load(InstrumentationModule.class);
        moduleLoader.forEach(metricsRegistry::registerModule);

        // Wire to the metrics agent
        MetricsAgent.setRegistry(metricsRegistry);

        return metricsRegistry;
    }
}
```

### Configuration Options

You can control metrics via application properties:

```yaml
spring:
  actor:
    metrics:
      enabled: true  # Enable/disable metrics collection
      common-tags:
        application: my-app
        environment: production
```

Use `@ConditionalOnProperty` to make the bean conditional:

```java
@Bean
@ConditionalOnProperty(name = "spring.actor.metrics.enabled", havingValue = "true", matchIfMissing = true)
public MetricsRegistry actorMetricsRegistry(MeterRegistry meterRegistry) {
    // ... configuration
}
```

## Available Metrics

Once configured, the following actor metrics will be exported to your Micrometer registry:

### Actor Lifecycle
- `actor.created` (counter) - Number of actors created
- `actor.terminated` (counter) - Number of actors terminated
- `actor.active` (gauge) - Number of currently active actors

### Message Processing
- `actor.message.processed` (counter) - Number of messages processed
- `actor.message.processing.time` (timer) - Time spent processing messages
- `actor.message.failed` (counter) - Number of failed message processing attempts

### Mailbox
- `actor.mailbox.size` (gauge) - Current mailbox queue size
- `actor.mailbox.time` (timer) - Time messages spend in mailbox before processing

All metrics include dimensional tags:
- `actor.path` - The actor's path
- `message.type` - The message type (where applicable)
- Plus any custom tags you configure

## Viewing Metrics

### Prometheus
Access metrics at: `http://localhost:8080/actuator/prometheus`

### Grafana
Import the metrics from your Prometheus datasource to visualize actor behavior.

## Non-Spring Usage

The module works with any application that uses Micrometer:

```java
MeterRegistry registry = new SimpleMeterRegistry();
MetricsBackend backend = new MicrometerMetricsBackend(registry);

MetricsConfiguration config = MetricsConfiguration.builder()
    .enabled(true)
    .build();

MetricsRegistry metricsRegistry = MetricsRegistry.builder()
    .configuration(config)
    .backend(backend)
    .build();

// Register modules and wire to agent
ServiceLoader.load(InstrumentationModule.class)
    .forEach(metricsRegistry::registerModule);

MetricsAgent.setRegistry(metricsRegistry);
```

## Architecture

```
┌─────────────────┐
│  Java Agent     │  Applies ByteBuddy instrumentation at startup
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│ Instrumentation │  Discovers modules via SPI
│    Modules      │  (ActorLifecycle, MessageProcessing, Mailbox)
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│ MetricsRegistry │  Coordinates metric collection
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│ Micrometer      │  Bridges to Micrometer's MeterRegistry
│    Backend      │  (this module)
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│  MeterRegistry  │  Exports to Prometheus, Grafana, etc.
└─────────────────┘
```

## Examples

See the [chat example](../example/chat) for a complete Spring Boot integration example.

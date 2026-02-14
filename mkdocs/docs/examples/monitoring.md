# Actor Metrics (Experimental)

ByteBuddy-based instrumentation for Pekko actors. Tracks lifecycle, mailbox, message processing, and system metrics via Micrometer.

!!! warning "Experimental"
    APIs and metric names may change in future releases.

## Quick Start

### 1. Add Dependencies

```gradle
dependencies {
    implementation 'io.github.seonwkim:spring-boot-starter-actor-metrics:{version}'
    implementation 'org.springframework.boot:spring-boot-starter-actuator'
    implementation 'io.micrometer:micrometer-registry-prometheus'
}
```

### 2. Build and Run with the Agent

```bash
./gradlew :metrics:agentJar

java -javaagent:spring-boot-starter-actor-metrics-{version}-agent.jar -jar your-app.jar
```

You need **both** the `-javaagent` (bytecode rewriting) **and** the library dependency (metrics backend). Without either, nothing is recorded.

### 3. Expose Prometheus

```yaml
management:
  endpoints:
    web:
      exposure:
        include: prometheus, health, info, metrics
```

With `spring-boot-starter-actuator` on the classpath, metrics are auto-configured. Disable with `spring.actor.metrics.enabled=false`.

## Available Metrics

| Metric | Type | Tags | Description |
|---|---|---|---|
| `actor.lifecycle.created` | Counter | `actor.class` | Actors created |
| `actor.lifecycle.terminated` | Counter | `actor.class` | Actors terminated |
| `actor.lifecycle.restarts` | Counter | `actor.class` | Supervision restarts |
| `actor.lifecycle.resumes` | Counter | `actor.class` | Supervision resumes |
| `actor.lifecycle.active` | Gauge | -- | Currently active actors |
| `actor.mailbox.size` | Gauge | `actor.class` | Mailbox queue size |
| `actor.mailbox.size.max` | Gauge | `actor.class` | Peak mailbox size |
| `actor.mailbox.time` | Timer | `actor.class`, `message.type` | Time in mailbox |
| `actor.mailbox.overflow` | Counter | -- | Bounded mailbox drops |
| `actor.message.processed` | Counter | `actor.class`, `message.type` | Messages processed |
| `actor.message.processing.time` | Timer | `actor.class`, `message.type` | Processing duration |
| `actor.stash.size` | Gauge | `actor.class` | Stashed messages |
| `system.dead-letters` | Counter | -- | Undeliverable messages |
| `system.unhandled-messages` | Counter | -- | Unhandled messages |
| `scheduler.tasks.scheduled` | Counter | -- | Tasks via `scheduleOnce` |

Typed actors use `actor.class=TypedActor`; use `message.type` to distinguish behaviors.

## Configuration

### Environment Variables

```bash
ACTOR_METRICS_ENABLED=false                    # Kill switch
ACTOR_METRICS_TAG_APPLICATION=my-app           # Global tag
ACTOR_METRICS_TAG_ENVIRONMENT=prod             # Global tag
ACTOR_METRICS_SAMPLING_RATE=0.1                # 0.0-1.0

# Disable a specific module
ACTOR_METRICS_INSTRUMENT_MAILBOX=false
```

Module IDs: `actor-lifecycle`, `mailbox`, `message-processing`, `system`, `scheduler`, `stash`. Env var pattern: `ACTOR_METRICS_INSTRUMENT_{MODULE_ID}` (hyphens to underscores, uppercased).

### Programmatic

```java
@Bean
public MetricsRegistry actorMetricsRegistry(MeterRegistry meterRegistry) {
    return MicrometerMetricsRegistryBuilder.fromEnvironment(meterRegistry)
            .tag("custom-tag", "value")
            .sampling(SamplingConfig.rateBased(0.1))
            .build();
}
```

## How It Works

The agent must rewrite bytecode *before* Pekko classes load, but Micrometer only exists *after* Spring boots. So initialization happens in two phases:

1. **Agent (JVM startup):** `MetricsAgent.premain()` discovers modules via `ServiceLoader` and uses ByteBuddy to instrument Pekko classes. `MetricsAgent.getRegistry()` returns `null` -- no metrics yet.
2. **Registry (Spring context):** Auto-configuration creates a `MetricsRegistry` with a Micrometer backend, initializes modules, and calls `MetricsAgent.setRegistry()`. Metrics start recording.

A `BeanDefinitionRegistryPostProcessor` adds `depends-on` to ensure the registry is created before the `actorSystem` bean.

## Troubleshooting

| Problem | Solution |
|---|---|
| No metrics | Check logs for `[MetricsAgent] Metrics agent installed successfully`. Ensure `-javaagent` is on the command line. |
| Agent JAR missing | Run `./gradlew :metrics:agentJar`. |
| Prometheus empty | Set `management.endpoints.web.exposure.include=prometheus`. |
| Counters at zero | Counters appear after first use. Generate traffic first. |

## Next Steps

- [Chat Example](chat.md) - Metrics in a clustered application
- [Logging Guide](../guides/logging.md) - MDC logging for observability

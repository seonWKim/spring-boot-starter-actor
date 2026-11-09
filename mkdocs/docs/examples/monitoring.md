# Actor Metrics

Actor meters through Micrometer, with no Java agent: the actor system the starter builds records them from hooks it owns, a behavior interceptor, an instrumented default mailbox and the default dispatchers' executors.

## Quick Start

### 1. Add Dependencies

```gradle
dependencies {
    implementation 'io.github.seonwkim:actor-metrics:{version}'
    implementation 'org.springframework.boot:spring-boot-starter-actuator'
    implementation 'io.micrometer:micrometer-registry-prometheus'
}
```

### 2. Expose Prometheus

```yaml
management:
  endpoints:
    web:
      exposure:
        include: prometheus, health, info, metrics
```

That's all: with a `MeterRegistry` bean (Actuator registers one), every actor the starter spawns is metered. Disable with `spring.actor.metrics.enabled=false`.

## Actor meters

| Meter | Type | Tags | What |
|---|---|---|---|
| `actor.message.processed` | Counter | `actor.class`, `dispatcher`, `system` | Messages actors processed |
| `actor.message.processing.time` | Timer | `actor.class`, `dispatcher`, `system` | Time spent processing a message |
| `actor.message.failures` | Counter | `actor.class`, `dispatcher`, `system` | Messages whose processing threw |
| `actor.lifecycle.restarts` | Counter | `actor.class`, `dispatcher`, `system` | Restarts by supervision |
| `actor.lifecycle.active` | Gauge | `actor.class`, `dispatcher`, `system` | Actors running |
| `actor.mailbox.size` | Gauge | `actor.class`, `dispatcher`, `system` | Messages waiting in mailboxes |
| `actor.mailbox.time` | Timer | `actor.class`, `dispatcher`, `system` | Time a message waited in the mailbox |
| `actor.dispatcher.threads` | Gauge | `dispatcher`, `system` | Threads in the dispatcher's pool |
| `actor.dispatcher.threads.active` | Gauge | `dispatcher`, `system` | Threads running tasks |
| `actor.dispatcher.queued` | Gauge | `dispatcher`, `system` | Tasks waiting for a thread |

- `actor.class` is the simple name of the actor's class (for a sharded actor, its `SpringShardedActor` class), `dispatcher` the Pekko dispatcher id (`pekko.actor.default-dispatcher`), `system` the actor system's name.
- Meters are per actor class, not per actor, so the series count does not grow with the number of actors: 10,000 entities of one class have the same series as one. A test spawns 10,000 actors and checks it.
- **Path tags** are opt-in and capped: `spring.actor.metrics.path-tag-limit=50` adds `actor.path` to the `actor.message.*` meters for the first 50 actor paths seen, and `actor.path=other` for every other actor.
- The cluster adds the [`actor.cluster.*` meters](../guides/production.md#cluster-metrics).

What is not covered:

- Mailbox meters and `actor.lifecycle.active` count actors on the default mailbox. An actor spawned with a bounded or a configured mailbox keeps that mailbox and has only the `actor.message.*` and restart meters.
- Dispatcher meters cover the default and the blocking dispatcher (and dispatchers that inherit their executor) on Pekko's fork-join or thread-pool executor. A dispatcher on a Spring `Executor` bean is measured by Spring Boot's own executor metrics; one on virtual threads has no pool to measure.
- A behavior from `SpringActorBehavior.wrap` (a raw Pekko behavior) is metered outside its own supervision, so its failures and restarts are not seen.

### Overhead

`ActorMetersBenchmark` (`./gradlew :actor-autoconfigure:actorMetersBenchmark`) sends bursts of 1,000,000 tells to one actor in two actor systems side by side, one with the meters off and one with them on, alternating rounds. On an Apple M5 laptop with Java 21 and a `SimpleMeterRegistry` (the machine was busy with other builds, so treat the numbers as rough):

| | ns per message (median of 15 rounds) |
|---|---|
| meters off | 126 |
| meters on | 379 |
| overhead | about 250 |

The cost per message is two timer updates (processing time and time in mailbox), four `System.nanoTime()` calls and one small object in the mailbox; `actor.message.processed` reads the processing timer's count and costs nothing. An actor that does real work (microseconds per message) pays a small fraction of that; one that only forwards messages pays about three times the bare cost. Run the benchmark on your hardware before relying on these numbers, and set `spring.actor.metrics.enabled=false` where the overhead matters more than the meters.

## Optional: the Java agent

The ByteBuddy agent in `actor-metrics` instruments Pekko internals for a few meters no public hook exposes (stash size, dead letters, unhandled messages, scheduled tasks, per-message-type tags). It breaks more easily with Pekko upgrades, so use it only when you need those.

```bash
./gradlew :actor-metrics:agentJar

java -javaagent:actor-metrics-{version}-agent.jar -jar your-app.jar
```

With the agent attached, it records the message, mailbox and lifecycle meters below under the same names, and the hooks register only `actor.message.failures` and the `actor.dispatcher.*` meters: no meter is registered twice.

### Agent meters

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

### Agent configuration

#### Environment Variables

```bash
ACTOR_METRICS_ENABLED=false                    # Kill switch
ACTOR_METRICS_TAG_APPLICATION=my-app           # Global tag
ACTOR_METRICS_TAG_ENVIRONMENT=prod             # Global tag
ACTOR_METRICS_SAMPLING_RATE=0.1                # 0.0-1.0

# Disable a specific module
ACTOR_METRICS_INSTRUMENT_MAILBOX=false
```

Module IDs: `actor-lifecycle`, `mailbox`, `message-processing`, `system`, `scheduler`, `stash`. Env var pattern: `ACTOR_METRICS_INSTRUMENT_{MODULE_ID}` (hyphens to underscores, uppercased).

#### Programmatic

```java
@Bean
public MetricsRegistry actorMetricsRegistry(MeterRegistry meterRegistry) {
    return MicrometerMetricsRegistryBuilder.fromEnvironment(meterRegistry)
            .tag("custom-tag", "value")
            .sampling(SamplingConfig.rateBased(0.1))
            .build();
}
```

### How the agent works

The agent must rewrite bytecode *before* Pekko classes load, but Micrometer only exists *after* Spring boots. So initialization happens in two phases:

1. **Agent (JVM startup):** `MetricsAgent.premain()` discovers modules via `ServiceLoader` and uses ByteBuddy to instrument Pekko classes. `MetricsAgent.getRegistry()` returns `null` -- no metrics yet.
2. **Registry (Spring context):** Auto-configuration creates a `MetricsRegistry` with a Micrometer backend, initializes modules, and calls `MetricsAgent.setRegistry()`. Metrics start recording.

A `BeanDefinitionRegistryPostProcessor` adds `depends-on` to ensure the registry is created before the `actorSystem` bean.

## Troubleshooting

| Problem | Solution |
|---|---|
| No agent metrics | Check logs for `[MetricsAgent] Metrics agent installed successfully`. Ensure `-javaagent` is on the command line. |
| Agent JAR missing | Run `./gradlew :actor-metrics:agentJar`. |
| Prometheus empty | Set `management.endpoints.web.exposure.include=prometheus`. |
| Counters at zero | Counters appear after first use. Generate traffic first. |

## Next Steps

- [Chat Example](chat.md) - Metrics in a clustered application
- [Logging Guide](../guides/logging.md) - MDC logging for observability

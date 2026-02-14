# Actor Metrics(Experimental)

ByteBuddy-based instrumentation for Pekko actors. Tracks lifecycle, mailbox, message processing, and system metrics via
Micrometer.

Note that this is an experimental feature.

## How It Initializes

The metrics system starts in two phases because the agent must rewrite bytecode *before* Pekko classes load, but the
Micrometer backend only exists *after* Spring boots.

```
JVM startup                          Spring context ready
     |                                        |
     v                                        v
 MetricsAgent.premain()               MicrometerMetricsRegistryBuilder.build()
     |                                        |
     +-- ServiceLoader discovers              +-- Creates MicrometerMetricsBackend
     |   InstrumentationModules               +-- Reads env vars for config
     +-- ByteBuddy rewrites Pekko             +-- ServiceLoader discovers modules
     |   classes (ActorCell, etc.)             +-- Calls module.initialize()
     +-- Instrumented code calls              +-- Calls MetricsAgent.setRegistry()
         MetricsAgent.getRegistry()                |
         which returns null until -----------------.
         registry is set
```

**Phase 1 -- Agent (JVM startup):** `MetricsAgent.premain()` runs, discovers modules via `ServiceLoader`, and uses
ByteBuddy to instrument Pekko internal classes (`ActorCell`, `EventStream`, `Envelope`, etc.). Instrumented advice
methods call `MetricsAgent.getRegistry()` -- which returns `null` until Phase 2 completes. No metrics are recorded yet.

**Phase 2 -- Registry (Spring context):** The auto-configuration (or your manual bean) creates a `MetricsRegistry` with
a Micrometer backend, discovers the same modules via `ServiceLoader` again (this time calling `initialize()` on each),
and stores the registry in `MetricsAgent.setRegistry()`. From this point on, instrumented code records metrics.

**Bean ordering:** A `BeanDefinitionRegistryPostProcessor` adds `depends-on="actorMetricsRegistry"` to the `actorSystem`
bean, ensuring the registry is wired before the ActorSystem creates any actors.

## Quick Start

### 1. Add dependencies

```gradle
dependencies {
    implementation 'io.github.seonwkim:spring-boot-starter-actor-metrics:{version}'
    implementation 'org.springframework.boot:spring-boot-starter-actuator'
    // For Prometheus: implementation 'io.micrometer:micrometer-registry-prometheus'
}
```

### 2. Get the agent JAR

```bash
# Build from source
./gradlew :metrics:agentJar
# Output: metrics/build/libs/spring-boot-starter-actor-metrics-{version}-agent.jar

# Or download from GitHub Releases
```

### 3. Run with `-javaagent`

```bash
java -javaagent:spring-boot-starter-actor-metrics-{version}-agent.jar -jar your-app.jar
```

You need **both** the `-javaagent` (bytecode instrumentation) **and** the library dependency (metrics collection).
Without the agent, no bytecode is rewritten. Without the dependency, there's no backend to record to.

### 4. Auto-configuration

With `spring-boot-starter-actuator` on the classpath, metrics are auto-configured. No extra config needed.

- Disable: `spring.actor.metrics.enabled=false`
- Manual bean (when auto-config doesn't apply):

```java

@Bean
public MetricsRegistry actorMetricsRegistry(MeterRegistry meterRegistry) {
    return MicrometerMetricsRegistryBuilder.fromEnvironment(meterRegistry).build();
}
```

## Available Metrics

| Metric                          | Type    | Tags                          | Description                        |
|---------------------------------|---------|-------------------------------|------------------------------------|
| `actor.lifecycle.created`       | Counter | `actor.class`                 | Actors created                     |
| `actor.lifecycle.terminated`    | Counter | `actor.class`                 | Actors terminated                  |
| `actor.lifecycle.restarts`      | Counter | `actor.class`                 | Supervision Restart                |
| `actor.lifecycle.resumes`       | Counter | `actor.class`                 | Supervision Resume                 |
| `actor.lifecycle.active`        | Gauge   | --                            | Currently active actors            |
| `actor.mailbox.size`            | Gauge   | `actor.class`                 | Mailbox queue size                 |
| `actor.mailbox.size.max`        | Gauge   | `actor.class`                 | Peak mailbox size                  |
| `actor.mailbox.time`            | Timer   | `actor.class`, `message.type` | Time in mailbox                    |
| `actor.mailbox.overflow`        | Counter | --                            | Bounded mailbox drops              |
| `actor.message.processed`       | Counter | `actor.class`, `message.type` | Messages processed                 |
| `actor.message.processing.time` | Timer   | `actor.class`, `message.type` | Processing duration                |
| `actor.stash.size`              | Gauge   | `actor.class`                 | Stashed messages                   |
| `system.dead-letters`           | Counter | --                            | Undeliverable messages             |
| `system.unhandled-messages`     | Counter | --                            | Unhandled messages                 |
| `scheduler.tasks.scheduled`     | Counter | --                            | Tasks scheduled via `scheduleOnce` |

Typed actors (from `spring-boot-starter-actor`) use `actor.class=TypedActor`. Use `message.type` to distinguish
behaviors.

## Configuration

### Environment variables

```bash
ACTOR_METRICS_ENABLED=false                          # Kill switch
ACTOR_METRICS_TAG_APPLICATION=my-app                 # Global tag
ACTOR_METRICS_TAG_ENVIRONMENT=prod                   # Global tag
ACTOR_METRICS_SAMPLING_RATE=0.1                      # 0.0-1.0

# Disable a specific module (zero overhead -- bytecode not rewritten)
ACTOR_METRICS_INSTRUMENT_MAILBOX=false
```

Module IDs: `actor-lifecycle`, `mailbox`, `message-processing`, `system`, `scheduler`, `stash`

Env var pattern: `ACTOR_METRICS_INSTRUMENT_{MODULE_ID}` (hyphens become underscores, uppercased).

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

## Design Choices

### Why a Java agent?

Pekko's internal classes (`ActorCell`, `Envelope`, `EventStream`) are not extensible. There are no public hooks for
mailbox enqueue/dequeue timing, actor creation/termination callbacks, or dead letter interception at the level we need.
ByteBuddy advice injected via `-javaagent` is the only way to instrument these without forking Pekko.

### Why two-phase initialization?

The agent must run at JVM startup (`premain`) to rewrite class bytecode *before* the JVM loads those classes. But the
Micrometer `MeterRegistry` only exists after Spring Boot initializes. So instrumentation happens early (Phase 1), and
the metrics backend connects later (Phase 2). Between phases, all `MetricsAgent.getRegistry()` calls return `null` and
advice methods silently no-op.

### Single registry per JVM

`MetricsAgent` holds one static `MetricsRegistry`. All `ActorSystem` instances in the same JVM share it. Calling
`setRegistry()` again replaces the previous one (useful in tests). This is intentional -- ByteBuddy advice is
JVM-global, so the registry must be too.

### Static and ThreadLocal state in MetricsContext

Modules need mutable state (mailbox sizes, envelope timestamps, stash counts) that outlives any single method call. This
state lives in `MetricsContext`, owned by the `MetricsRegistry`. Key patterns:

- **`ConcurrentHashMap<String, AtomicLong>`** for gauges aggregated by `actor.class` (not per-instance, to avoid
  cardinality explosion)
- **`WeakHashMap<Object, Long>`** for envelope timestamps (allows GC of envelopes)
- **`ThreadLocal<String>`** to propagate actor class from `ActorCell.invoke()` to `StashBuffer.stash()`, since stash
  calls happen on the same thread as message dispatch but don't have direct access to the actor context

All state is cleared on `MetricsRegistry.shutdown()`.

### `actor.class` over `actor.path`

Actor paths (e.g., `/user/myActor/$a`) are high-cardinality and would cause metric explosion. We tag with `actor.class`
instead, which groups all instances of the same actor type. System actors (`/system/*`) and temporary actors (`/temp/*`,
`$`) are filtered out entirely.

### Scheduler: only `scheduleOnce` is instrumented

Scala trait method dispatch makes matching `scheduleWithFixedDelay` / `scheduleAtFixedRate` unreliable across
Pekko/Scala versions. We instrument `scheduleOnce` on `LightArrayRevolverScheduler` which is concrete and stable.

### `@ConditionalOnBean` on method, not class

The auto-configuration places `@ConditionalOnBean(MeterRegistry.class)` on the `@Bean` method rather than the
`@Configuration` class. This ensures the condition is evaluated during bean creation (not class processing), making
`MeterRegistry` visible regardless of whether the config is loaded via `spring.factories` or `@Import`.

## Troubleshooting

1. **No metrics?** Check logs for `[MetricsAgent] Metrics agent installed successfully` and
   `[MetricsAgent] MetricsRegistry set`.
2. **Agent JAR missing?** Run `./gradlew :metrics:agentJar` first.
3. **Prometheus not showing?** Set `management.endpoints.web.exposure.include=prometheus`.
4. **Counters at zero?** Counters/timers appear only after first use. Generate some traffic first.

## Examples

- [example/metrics-example](../example/metrics-example) -- Minimal setup with auto-configuration
- [example/chat](../example/chat) -- Chat app with cluster sharding and metrics

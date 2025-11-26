# Metrics Module

ByteBuddy-based instrumentation for Pekko actors. Tracks lifecycle, mailbox, and message processing metrics with low-cardinality tags.

## Quick Start

> **Important:** This module requires **BOTH** the Java agent (for bytecode instrumentation) AND the Spring Boot dependency (for metrics collection). Neither works without the other.

### 1. Add Dependency

```gradle
dependencies {
    // Provides Micrometer backend and builder
    // Includes metrics core module transitively
    implementation 'io.github.seonwkim:spring-boot-starter-actor-metrics-micrometer:{version}'
}
```

### 2. Start with Java Agent (Required!)

```bash
# The agent applies bytecode instrumentation at JVM startup
# Without this, no metrics will be collected even if you have the dependency
java -javaagent:metrics-{version}-agent.jar -jar your-app.jar
```

### 3. Configure Spring Bean (Required!)

```java
@Configuration
public class ActorMetricsConfiguration {

    @Bean
    public MetricsRegistry actorMetricsRegistry(MeterRegistry meterRegistry) {
        // Automatically loads configuration from environment variables
        return MicrometerMetricsRegistryBuilder.fromEnvironment(meterRegistry).build();
    }
}
```

That's it! The builder automatically:
- Reads environment variables for configuration
- Creates MicrometerMetricsBackend with your MeterRegistry
- Auto-discovers and registers all modules via ServiceLoader
- Wires the registry to the agent

## How It Works: Two-Phase Initialization

1. **Phase 1 - JVM Startup (Agent)**:
   - Agent applies bytecode instrumentation to Pekko actor classes
   - Inserts metric collection code into methods like `invoke()`, `newActor()`, etc.
   - Waits for application to provide MetricsRegistry

2. **Phase 2 - Spring Ready (Your Bean)**:
   - Spring creates `MeterRegistry` bean
   - Your configuration creates `MetricsRegistry` with Micrometer backend
   - Wires registry to agent via `MetricsAgent.setRegistry()`
   - Instrumented code now records metrics to Micrometer

> **Why two phases?** The agent runs at JVM startup before Spring's `MeterRegistry` exists. The agent instruments the code but can't collect metrics yet. Your Spring bean creates the backend and wires it to the agent once Spring is ready.

**Advanced usage:**

```java
// Mix environment config with code config
@Bean
public MetricsRegistry actorMetricsRegistry(MeterRegistry meterRegistry) {
    return MicrometerMetricsRegistryBuilder.fromEnvironment(meterRegistry)
        .tag("custom-tag", "custom-value")  // Override or add tags
        .module("mailbox", ModuleConfig.disabled())  // Override module config
        .build();
}

// Pure code configuration (ignores environment variables)
@Bean
public MetricsRegistry actorMetricsRegistry(MeterRegistry meterRegistry) {
    return MicrometerMetricsRegistryBuilder.create(meterRegistry)
        .enabled(true)
        .tag("application", "my-app")
        .sampling(SamplingConfig.rateBased(0.1))
        .build();
}
```

See full example: [ActorMetricsConfiguration.java](../example/chat/src/main/java/io/github/seonwkim/example/config/ActorMetricsConfiguration.java)

## Metrics

| Metric | Type | Tags | Description |
|--------|------|------|-------------|
| `actor.lifecycle.created` | Counter | `actor.class` | Actors created |
| `actor.lifecycle.terminated` | Counter | `actor.class` | Actors terminated |
| `actor.lifecycle.active` | Gauge | - | Active actors (global) |
| `actor.mailbox.size` | Gauge | `actor.class` | Mailbox size per class |
| `actor.mailbox.time` | Timer | `actor.class`, `message.type` | Time in mailbox |
| `actor.message.processed` | Counter | `actor.class`, `message.type` | Messages processed |
| `actor.message.processing.time` | Timer | `actor.class`, `message.type` | Processing time |

## Configuration

### Via Environment Variables

Control metrics behavior without code changes:

```bash
# Master switch: disable ALL metrics (skips instrumentation and collection)
ACTOR_METRICS_ENABLED=false  # Default: true

# Global tags (only applies if ACTOR_METRICS_ENABLED=true)
ACTOR_METRICS_TAG_APPLICATION=my-app
ACTOR_METRICS_TAG_ENVIRONMENT=prod

# Sampling rate (0.0 to 1.0)
ACTOR_METRICS_SAMPLING_RATE=0.1

# Per-module instrumentation control (JVM startup, fine-grained)
ACTOR_METRICS_INSTRUMENT_MAILBOX=false              # Don't instrument mailbox code
ACTOR_METRICS_INSTRUMENT_MESSAGE_PROCESSING=false   # Don't instrument message processing code

# Per-module collection control (runtime, fine-grained)
ACTOR_METRICS_MODULE_MAILBOX_ENABLED=false          # Don't collect mailbox metrics
ACTOR_METRICS_MODULE_MESSAGE_PROCESSING_ENABLED=false  # Don't collect message metrics

java -javaagent:metrics-agent.jar -jar app.jar
```

**Configuration Levels (in order of precedence):**

1. **`ACTOR_METRICS_ENABLED=false`** - Disables everything (zero overhead, no instrumentation)
2. **`ACTOR_METRICS_INSTRUMENT_*=false`** - Disables specific module instrumentation (use if you never need those metrics)
3. **`ACTOR_METRICS_MODULE_*_ENABLED=false`** - Disables specific module collection at runtime (temporary disable)

### Via Code

```java
MetricsConfiguration config = MetricsConfiguration.builder()
    .enabled(true)
    .tag("environment", "prod")
    .sampling(MetricsConfiguration.SamplingConfig.rateBased(0.1))  // Sample 10%
    .filters(MetricsConfiguration.FilterConfig.builder()
        .includeActors("**/user/my-service/**")  // Only specific actors
        .excludeActors("**/user/debug/**")       // Exclude debug actors
        .build())
    .module("mailbox", MetricsConfiguration.ModuleConfig.disabled())
    .build();
```

> **Note:** System actors (`/system/*`) and temporary actors (`/temp/*` or with `$` in path) are automatically excluded - no need to configure filters for them.

## Module Selection

All modules are **auto-discovered via ServiceLoader**. You have **two levels of control**:

### Level 1: Bytecode Instrumentation (Agent Startup)

Control which modules instrument bytecode at JVM startup:

```bash
# Disable mailbox instrumentation entirely (no bytecode transformation)
ACTOR_METRICS_INSTRUMENT_MAILBOX=false java -javaagent:metrics-agent.jar -jar app.jar

# Disable multiple modules
ACTOR_METRICS_INSTRUMENT_MAILBOX=false \
ACTOR_METRICS_INSTRUMENT_MESSAGE_PROCESSING=false \
java -javaagent:metrics-agent.jar -jar app.jar
```

**Why use this?** Zero overhead - if a module isn't instrumented, there's no bytecode transformation at all.

**Environment variables:**
- `ACTOR_METRICS_INSTRUMENT_ACTOR_LIFECYCLE=false` - Skip lifecycle instrumentation
- `ACTOR_METRICS_INSTRUMENT_MAILBOX=false` - Skip mailbox instrumentation
- `ACTOR_METRICS_INSTRUMENT_MESSAGE_PROCESSING=false` - Skip message processing instrumentation

### Level 2: Metrics Collection (Runtime)

Control which modules collect metrics (even if instrumented):

```bash
# Bytecode is instrumented but metrics aren't collected
ACTOR_METRICS_MODULE_MAILBOX_ENABLED=false java -javaagent:metrics-agent.jar -jar app.jar
```

**Or via configuration code:**
```java
MetricsConfiguration config = MetricsConfiguration.builder()
    .enabled(true)
    .module("mailbox", ModuleConfig.disabled())
    .module("message-processing", ModuleConfig.disabled())
    .build();
```

**Why use this?** Temporarily disable metrics without restarting the JVM.

### Comparison

| Level | When Applied | Overhead if Disabled | Use Case |
|-------|-------------|----------------------|----------|
| Instrumentation (`ACTOR_METRICS_INSTRUMENT_*`) | JVM startup (agent premain) | Zero (no bytecode changes) | Production: never need these metrics |
| Collection (`ACTOR_METRICS_MODULE_*_ENABLED`) | Runtime (application ready) | Minimal (instrumented but no recording) | Temporarily disable for debugging |

**Available Module IDs:**
- `actor-lifecycle` - Lifecycle metrics (created, terminated, active)
- `mailbox` - Mailbox metrics (size, time)
- `message-processing` - Message metrics (processed count, processing time)

## Custom Modules

You can create your own instrumentation modules:

```java
public class CustomSupervisionModule implements InstrumentationModule {

    private static final String METRIC_SUPERVISION_FAILURES = "actor.supervision.failures";

    @Override
    public String moduleId() {
        return "custom-supervision";
    }

    @Override
    public String description() {
        return "Tracks actor supervision failures";
    }

    @Override
    public void initialize(MetricsRegistry registry) {
        logger.info("Initializing Custom Supervision Module");
    }

    @Override
    public void shutdown() {
        logger.info("Shutting down Custom Supervision Module");
    }

    /**
     * ByteBuddy instrumentation - MUST be public static.
     */
    public static AgentBuilder instrument(AgentBuilder builder) {
        return builder
            .type(ElementMatchers.named("org.apache.pekko.actor.ActorCell"))
            .transform((builderParam, typeDescription, classLoader, module) ->
                builderParam.visit(Advice.to(SupervisionAdvice.class)
                    .on(ElementMatchers.named("handleInvokeFailure"))));
    }

    public static class SupervisionAdvice {
        @Advice.OnMethodEnter(suppress = Throwable.class)
        public static void onEnter(@Advice.This Object actorCell, @Advice.Argument(1) Throwable cause) {
            try {
                MetricsRegistry reg = MetricsAgent.getRegistry();
                if (reg == null) return;

                ActorContext context = ActorContext.from(actorCell);
                if (!reg.shouldInstrument(context)) return;

                Tags tags = context.toTags()
                    .and("error.type", cause.getClass().getSimpleName())
                    .and(reg.getGlobalTags());

                Counter counter = reg.getBackend().counter(METRIC_SUPERVISION_FAILURES, tags);
                counter.increment();
            } catch (Exception e) {
                // Silently fail
            }
        }
    }
}
```

**Register your module via ServiceLoader:**

Custom modules **must** be registered via ServiceLoader because bytecode instrumentation happens at JVM startup (before your application runs).

1. Create `META-INF/services/io.github.seonwkim.metrics.api.InstrumentationModule`:
```
com.yourcompany.CustomSupervisionModule
```

2. Ensure your module JAR is on the classpath when the agent starts:
```bash
java -javaagent:metrics-{version}-agent.jar -cp your-module.jar:app.jar Main
```

The agent will automatically discover and instrument your module at startup.

**Requirements:**
- Implement `InstrumentationModule` interface
- Add `public static AgentBuilder instrument(AgentBuilder)` method for bytecode transformation
- Use `MetricsAgent.getRegistry()` to access registry in ByteBuddy advice
- Suppress exceptions (`suppress = Throwable.class`) to avoid disrupting actor system
- **Must use ServiceLoader** - manual registration won't apply bytecode instrumentation

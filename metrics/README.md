# Metrics Module

ByteBuddy-based instrumentation for Pekko actors. Tracks lifecycle, mailbox, and message processing metrics with low-cardinality tags.

## Quick Start

### 1. Add Dependency

```gradle
dependencies {
    // Includes metrics core module transitively
    implementation 'io.github.seonwkim:spring-boot-starter-actor-metrics-micrometer:{version}'
}
```

### 2. Start with Java Agent

```bash
java -javaagent:metrics-{version}-agent.jar -jar your-app.jar
```

### 3. Configure Spring Bean (Required for Spring Boot)

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

> **Why is the Spring bean required?** The agent runs at JVM startup before Spring's `MeterRegistry` exists. The agent applies bytecode instrumentation but can't collect metrics yet. This bean creates the backend later and wires it to the agent.

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
# Enable/disable metrics
METRICS_ENABLED=true

# Global tags
METRICS_TAG_APPLICATION=my-app
METRICS_TAG_ENVIRONMENT=prod

# Sampling rate (0.0 to 1.0)
METRICS_SAMPLING_RATE=0.1

# Disable specific modules
METRICS_MODULE_MAILBOX_ENABLED=false
METRICS_MODULE_MESSAGE_PROCESSING_ENABLED=false

java -javaagent:metrics-agent.jar -jar app.jar
```

### Via Code

```java
MetricsConfiguration config = MetricsConfiguration.builder()
    .enabled(true)
    .tag("environment", "prod")
    .sampling(MetricsConfiguration.SamplingConfig.rateBased(0.1))  // Sample 10%
    .filters(MetricsConfiguration.FilterConfig.builder()
        .includeActors("**/user/**")
        .excludeActors("**/system/**")
        .build())
    .module("mailbox", MetricsConfiguration.ModuleConfig.disabled())
    .build();
```

## Module Selection

All modules are **auto-discovered via ServiceLoader** and registered by default. You can opt-out specific modules:

### Via Environment Variables (Recommended)
```bash
# Disable mailbox metrics in production
METRICS_MODULE_MAILBOX_ENABLED=false java -javaagent:metrics-agent.jar -jar app.jar

# Disable multiple modules
METRICS_MODULE_MAILBOX_ENABLED=false \
METRICS_MODULE_MESSAGE_PROCESSING_ENABLED=false \
java -javaagent:metrics-agent.jar -jar app.jar
```

### Via Configuration Code
```java
MetricsConfiguration config = MetricsConfiguration.builder()
    .enabled(true)
    .module("mailbox", ModuleConfig.disabled())
    .module("message-processing", ModuleConfig.disabled())
    .build();
```

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

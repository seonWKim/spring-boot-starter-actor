# Actor Metrics

ByteBuddy-based instrumentation for Pekko actors. Tracks lifecycle, mailbox, and message processing metrics using Micrometer.

## Quick Start

### 1. Add Dependencies

**For development:**
```gradle
dependencies {
    implementation 'io.github.seonwkim:metrics:{version}'
    implementation 'org.springframework.boot:spring-boot-starter-actuator'  // Includes micrometer-core
}
```

**For production (add your monitoring system):**
```gradle
dependencies {
    implementation 'io.github.seonwkim:metrics:{version}'
    implementation 'org.springframework.boot:spring-boot-starter-actuator'
    implementation 'io.micrometer:micrometer-registry-prometheus'  // For Prometheus/Grafana
    // OR
    // implementation 'io.micrometer:micrometer-registry-datadog'  // For DataDog
    // implementation 'io.micrometer:micrometer-registry-graphite'  // For Graphite
}
```

### 2. Download the Agent JAR

**Option A: Download from GitHub Releases (Recommended)**

```bash
# Download the latest agent JAR from GitHub releases
./gradlew downloadMetricsAgent

# Or download manually:
# https://github.com/seonwkim/spring-boot-starter-actor/releases/latest
```

**Option B: Use from Maven Local (For Development)**

```bash
# Build and install to Maven local repository
./gradlew :metrics:agentJar :metrics:publishToMavenLocal
```

The agent JAR will be available at:
- **GitHub download**: `./build/agent/spring-boot-starter-actor-metrics-{version}-agent.jar`
- **Maven local**: `~/.m2/repository/io/github/seonwkim/spring-boot-starter-actor-metrics/{version}/spring-boot-starter-actor-metrics-{version}-agent.jar`

### 3. Run with Agent

**For development with Gradle:**

```bash
# Option 1: Configure JVM args in build.gradle.kts
tasks.named<org.springframework.boot.gradle.tasks.run.BootRun>("bootRun") {
    jvmArgs = listOf("-javaagent:build/agent/spring-boot-starter-actor-metrics-{version}-agent.jar")
}

./gradlew bootRun

# Option 2: Pass as command line argument
./gradlew bootRun -Dagent.path=build/agent/spring-boot-starter-actor-metrics-{version}-agent.jar
```

**For production with java -jar:**

```bash
# Build the JAR
./gradlew bootJar

# Run with agent
java -javaagent:build/agent/spring-boot-starter-actor-metrics-{version}-agent.jar -jar build/libs/your-app.jar
```

> **Important:** You need **BOTH** the agent (for instrumentation) AND the dependency (for metrics collection).

### 4. Configure Spring Bean

```java
@Configuration
public class ActorMetricsConfiguration {
    @Bean
    public MetricsRegistry actorMetricsRegistry(MeterRegistry meterRegistry) {
        return MicrometerMetricsRegistryBuilder.fromEnvironment(meterRegistry).build();
    }
}
```

Done! Metrics are automatically:
- Read from environment variables
- Instrumented via the agent
- Exported to your chosen registry (Prometheus, Grafana, etc.)

## Available Metrics

| Metric | Type | Tags | Description |
|--------|------|------|-------------|
| `actor.lifecycle.created` | Counter | `actor.class` | Actors created |
| `actor.lifecycle.terminated` | Counter | `actor.class` | Actors terminated |
| `actor.lifecycle.active` | Gauge | - | Active actors |
| `actor.mailbox.size` | Gauge | `actor.class` | Mailbox queue size |
| `actor.mailbox.time` | Timer | `actor.class`, `message.type` | Time message spends in mailbox |
| `actor.message.processed` | Counter | `actor.class`, `message.type` | Messages processed |
| `actor.message.processing.time` | Timer | `actor.class`, `message.type` | Message processing duration |

## Configuration

### Environment Variables

```bash
# Master switch (disables all metrics)
ACTOR_METRICS_ENABLED=false

# Global tags
ACTOR_METRICS_TAG_APPLICATION=my-app
ACTOR_METRICS_TAG_ENVIRONMENT=prod

# Sampling (0.0 to 1.0)
ACTOR_METRICS_SAMPLING_RATE=0.1

# Disable specific modules at JVM startup (zero overhead)
ACTOR_METRICS_INSTRUMENT_MAILBOX=false

# Disable specific modules at runtime (minimal overhead)
ACTOR_METRICS_MODULE_MAILBOX_ENABLED=false
```

### Programmatic Configuration

```java
@Bean
public MetricsRegistry actorMetricsRegistry(MeterRegistry meterRegistry) {
    return MicrometerMetricsRegistryBuilder.fromEnvironment(meterRegistry)
        .tag("custom-tag", "value")
        .sampling(SamplingConfig.rateBased(0.1))
        .module("mailbox", ModuleConfig.disabled())
        .build();
}
```

## How It Works

**Two-Phase Initialization:**

1. **JVM Startup (Agent):** Instruments Pekko actor classes with ByteBuddy
2. **Spring Ready (Your Bean):** Creates Micrometer backend and wires to agent

**Filtering:**
- System actors (`/system/*`) and temporary actors (`/temp/*`, `$`) are automatically excluded
- Low-cardinality tags (`actor.class` instead of `actor.path`) prevent metric explosion

## Module Control

Available modules:
- `actor-lifecycle` - Creation, termination, active count
- `mailbox` - Queue size, wait time
- `message-processing` - Processing count, duration

**Two levels of control:**

| Level | Env Var | When Applied | Overhead if Disabled |
|-------|---------|--------------|---------------------|
| Instrumentation | `ACTOR_METRICS_INSTRUMENT_*=false` | JVM startup | Zero (no bytecode changes) |
| Collection | `ACTOR_METRICS_MODULE_*_ENABLED=false` | Runtime | Minimal (instrumented but not recorded) |

## Example

See [ActorMetricsConfiguration.java](../example/chat/src/main/java/io/github/seonwkim/example/config/ActorMetricsConfiguration.java) for a complete working example.

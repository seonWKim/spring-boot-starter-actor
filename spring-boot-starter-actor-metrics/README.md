# Spring Boot Starter Actor Metrics

Auto-configuration for actor metrics with Micrometer.

## Quick Start

### 1. Add Dependencies

```gradle
dependencies {
    implementation 'io.github.seonwkim:spring-boot-starter-actor-metrics:{version}'
    implementation 'org.springframework.boot:spring-boot-starter-actuator'
    implementation 'io.micrometer:micrometer-registry-prometheus'  // or your preferred registry
}
```

### 2. Download Agent JAR

Download `spring-boot-starter-actor-metrics-{version}-agent.jar` from [GitHub Releases](https://github.com/seonwkim/spring-boot-starter-actor/releases/latest).

Or build locally:
```bash
./gradlew :metrics:agentJar
# Output: metrics/build/libs/spring-boot-starter-actor-metrics-{version}-agent.jar
```

### 3. Run with Agent

**Development:**
```bash
./gradlew bootRun -Dagent=/path/to/metrics-agent.jar
```

**Production:**
```bash
java -javaagent:/path/to/metrics-agent.jar -jar your-app.jar
```

That's it! Metrics are automatically configured and available at `/actuator/metrics`.

## Available Metrics

| Metric | Type | Description |
|--------|------|-------------|
| `actor.lifecycle.created` | Counter | Actors created |
| `actor.lifecycle.terminated` | Counter | Actors terminated |
| `actor.lifecycle.active` | Gauge | Currently active actors |
| `actor.mailbox.size` | Gauge | Mailbox queue size |
| `actor.mailbox.time` | Timer | Time in mailbox |
| `actor.message.processed` | Counter | Messages processed |
| `actor.message.processing.time` | Timer | Message processing duration |

## Configuration

Environment variables:

```bash
# Global tags
ACTOR_METRICS_TAG_APPLICATION=my-app
ACTOR_METRICS_TAG_ENVIRONMENT=prod

# Sampling rate (0.0 - 1.0)
ACTOR_METRICS_SAMPLING_RATE=0.1

# Disable modules
ACTOR_METRICS_INSTRUMENT_MAILBOX=false
```

## How It Works

1. **Agent** (`-javaagent`) instruments Pekko actors with ByteBuddy at JVM startup
2. **Starter** (this module) auto-configures metrics when Spring Boot starts
3. **Micrometer** exports metrics via Spring Boot Actuator

System actors and temporary actors are automatically excluded.

## Example

See [example/chat](../example/chat) for a working example with Prometheus.
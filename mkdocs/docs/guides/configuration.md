# Configuration Reference

This page lists every configuration key the starter reads, with its default and what it does.
The same keys are published as Spring Boot configuration metadata
(`META-INF/spring-configuration-metadata.json` in the `core`, `core-boot3` and `metrics` jars),
so IDEs offer completion and documentation for them in `application.yml` and `application.properties`.

## Starter keys

These keys are read by the starter itself.

| Key | Module | Default | Description |
|---|---|---|---|
| `spring.actor.pekko.management.enabled` | core | `true` | Whether to start the Pekko Management HTTP server and Cluster Bootstrap when the actor system runs in cluster mode and the Pekko Management libraries are on the classpath. |
| `spring.actor.metrics.enabled` | metrics | `true` | Whether to register the actor `MetricsRegistry` bean that publishes actor metrics to the application's Micrometer `MeterRegistry`. |

```yaml
spring:
  actor:
    pekko:
      management:
        enabled: false   # do not start Pekko Management / Cluster Bootstrap
    metrics:
      enabled: false     # do not register the actor MetricsRegistry bean
```

## Pekko pass-through block (`spring.actor.*`)

Every other key under `spring.actor.*` is passed unchanged to the Pekko actor system configuration,
with the `spring.actor` prefix removed. For example, `spring.actor.pekko.loglevel` becomes
`pekko.loglevel`. Comma-separated string values are turned into lists. See the
[Pekko reference configuration](https://pekko.apache.org/docs/pekko/current/general/configuration-reference.html)
for the available keys.

The starter reads two of the pass-through keys while it builds the actor system:

| Key | Default | Description |
|---|---|---|
| `spring.actor.pekko.name` | `system` | Name of the Pekko actor system created by the starter. Passed through to Pekko as `pekko.name`. |
| `spring.actor.pekko.actor.provider` | `local` (Pekko default) | Pekko actor reference provider, passed through as `pekko.actor.provider`. Set to `cluster` to run the actor system in cluster mode. |

```yaml
spring:
  actor:
    pekko:
      name: my-system
      loglevel: INFO
      actor:
        provider: cluster
      cluster:
        seed-nodes: pekko://my-system@127.0.0.1:2551,pekko://my-system@127.0.0.1:2552
```

## Metrics agent environment variables (`ACTOR_METRICS_*`)

!!! warning "Not Spring keys"
    These are environment variables, read by the metrics Java agent and by the metrics registry
    builder with `System.getenv` (falling back to a JVM system property of the same name). They are
    not Spring configuration keys: setting them in `application.yml` or `application.properties`
    has no effect, and they do not appear in the Spring configuration metadata.

| Variable | Default | Description |
|---|---|---|
| `ACTOR_METRICS_ENABLED` | `true` | Read by the agent at JVM startup: `false` skips all instrumentation. Also read by the metrics registry builder to set the enabled flag of the metrics configuration. |
| `ACTOR_METRICS_INSTRUMENT_<MODULE>` | `true` | Read by the agent: `false` skips instrumentation for one module. `<MODULE>` is the module id in upper case with `-` replaced by `_`: `ACTOR_LIFECYCLE`, `MESSAGE_PROCESSING`, `MAILBOX`, `SYSTEM`, `SCHEDULER`, `STASH`. |
| `ACTOR_METRICS_TAG_<NAME>` | none | Adds a global tag to every actor metric. The tag name is `<NAME>` in lower case with `_` replaced by `.`; for example `ACTOR_METRICS_TAG_APPLICATION=my-app` adds the tag `application=my-app`. |
| `ACTOR_METRICS_SAMPLING_RATE` | `1.0` | Sampling rate between `0.0` and `1.0`. |

```bash
ACTOR_METRICS_TAG_APPLICATION=my-app \
ACTOR_METRICS_INSTRUMENT_MAILBOX=false \
java -javaagent:spring-boot-starter-actor-metrics-{version}-agent.jar -jar app.jar
```

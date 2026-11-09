# Configuration Reference

This page lists every configuration key the starter reads, with its default and what it does.
The same keys are published as Spring Boot configuration metadata
(`META-INF/spring-configuration-metadata.json` in the `actor-autoconfigure` and `actor-metrics` jars),
so IDEs offer completion and documentation for them in `application.yml` and `application.properties`.

## Typed keys

These keys are bound to `ActorProperties` and validated at startup: a bad value stops the
application with a message that names the key.

| Key | Default | Sets Pekko key | Description |
|---|---|---|---|
| `spring.actor.name` | `system` | `pekko.name` | Name of the actor system. Nodes of one cluster must use the same name. |
| `spring.actor.provider` | `local` | `pekko.actor.provider` | `local`, `remote` or `cluster`. `remote` and `cluster` need `actor-spring-boot-starter-cluster`. |
| `spring.actor.remote.hostname` | Pekko's default | `pekko.remote.artery.canonical.hostname` | Host name or address other nodes use to reach this node. |
| `spring.actor.remote.port` | Pekko's default (`25520`) | `pekko.remote.artery.canonical.port` | Port for other nodes, `0` to `65535`; `0` picks a free port. |
| `spring.actor.cluster.seed-nodes` | none | `pekko.cluster.seed-nodes` | Nodes to join at startup, as `pekko://<name>@<host>:<port>`. |
| `spring.actor.cluster.join-self` | `false` | none | Joins the node to itself: a one-node cluster without seed nodes, on any port (local runs, tests). Not with `seed-nodes`. |
| `spring.actor.cluster.roles` | none | `pekko.cluster.roles` | Roles of this node. Sharded actors and singletons can be limited to a role. |
| `spring.actor.dispatchers.<name>.executor` | none | `<name>.*` | Declares a dispatcher that runs on the named Spring `Executor` bean. See [Dispatchers](dispatchers.md). |
| `spring.actor.dispatchers.<name>.virtual` | `false` | `<name>.executor` | Declares a dispatcher that runs on virtual threads (instead of `executor`). |
| `spring.actor.virtual-threads.enabled` | `auto` | `pekko.actor.default-blocking-io-dispatcher.executor` | Runs `withBlockingDispatcher()` on virtual threads; `auto` follows `spring.threads.virtual.enabled`. |
| `spring.actor.dispatchers.<name>.throughput` | Pekko's default (`5`) | `<name>.throughput` | Messages an actor processes before its thread moves on. |
| `spring.actor.shutdown.timeout` | `20s` | | How long closing the Spring context waits for the actor system's coordinated shutdown. |
| `spring.actor.shutdown.exit-process` | `true` | | When the actor system terminates by itself (for example after this node is downed), the context closes; with this set, the process then exits with status 1. |
| `spring.actor.pekko.management.enabled` | `true` | | Whether to start the Pekko Management HTTP server and Cluster Bootstrap in cluster mode, when the Pekko Management libraries are on the classpath. |
| `spring.actor.metrics.enabled` | `true` | | Whether to register the actor `MetricsRegistry` bean (`actor-metrics`). |

```yaml
spring:
  actor:
    name: orders
    provider: cluster
    remote:
      hostname: 10.0.0.7
      port: 25520
    cluster:
      seed-nodes:
        - pekko://orders@10.0.0.7:25520
        - pekko://orders@10.0.0.8:25520
      roles: api
    shutdown:
      timeout: 25s
```

## Raw Pekko keys (`spring.actor.*`)

Every other key under `spring.actor.*` is passed unchanged to the Pekko actor system configuration,
with the `spring.actor` prefix removed. For example, `spring.actor.pekko.loglevel` becomes
`pekko.loglevel`, and a dispatcher defined at `spring.actor.my-dispatcher` becomes `my-dispatcher`.
Comma-separated string values are turned into lists. See the
[Pekko reference configuration](https://pekko.apache.org/docs/pekko/current/general/configuration-reference.html)
for the available keys.

```yaml
spring:
  actor:
    pekko:
      loglevel: INFO
      cluster:
        downing-provider-class: org.apache.pekko.cluster.sbr.SplitBrainResolverProvider
```

!!! note "Raw keys win"
    When a raw key and a typed key set the same Pekko key to different values, the raw key wins and
    the starter logs a warning that names both, for example
    `spring.actor.pekko.name=raw overrides spring.actor.name=typed`. Prefer the typed key.

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
java -javaagent:actor-metrics-{version}-agent.jar -jar app.jar
```

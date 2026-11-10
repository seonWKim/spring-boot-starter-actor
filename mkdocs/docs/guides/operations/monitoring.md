# Monitoring

This guide covers how to see what your actors do in production: Micrometer meters for actors, mailboxes,
dispatchers, the cluster and sharding; the read-only `actors` Actuator endpoint; traces that cross `tell`
and `ask`; log lines with actor context; and a ready-made Grafana dashboard with Prometheus alerts.

None of it needs a Java agent. An optional agent adds a few more meters (see
[The optional Java agent](#the-optional-java-agent)).

## Setup

Add `actor-metrics`, Actuator and a Micrometer registry (Prometheus here):

=== "Gradle"

    ```groovy
    dependencies {
        implementation 'io.github.seonwkim:actor-metrics:1.0.0'
        implementation 'org.springframework.boot:spring-boot-starter-actuator'
        implementation 'io.micrometer:micrometer-registry-prometheus'
    }
    ```

=== "Maven"

    ```xml
    <dependency>
        <groupId>io.github.seonwkim</groupId>
        <artifactId>actor-metrics</artifactId>
        <version>1.0.0</version>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-actuator</artifactId>
    </dependency>
    <dependency>
        <groupId>io.micrometer</groupId>
        <artifactId>micrometer-registry-prometheus</artifactId>
    </dependency>
    ```

Expose the Prometheus endpoint:

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
```

That is all. With a `MeterRegistry` bean (Actuator registers one), every actor the starter spawns is
metered. `spring.actor.metrics.enabled=false` turns every meter on this page off.

## Actor meters

The actor system the starter builds records these from hooks it owns: a behavior interceptor, an
instrumented default mailbox and the default dispatchers' executors.

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

- `actor.class` is the simple name of the actor's class (for a sharded actor, its `SpringShardedActor`
  class), `dispatcher` the Pekko dispatcher id (`pekko.actor.default-dispatcher`), `system` the actor
  system's name.
- Meters are per actor class, not per actor, so the series count does not grow with the number of actors:
  10,000 entities of one class have the same series as one. A test spawns 10,000 actors and checks it.
- **Path tags** are opt-in and capped: `spring.actor.metrics.path-tag-limit=50` adds `actor.path` to the
  `actor.message.*` meters for the first 50 actor paths seen, and `actor.path=other` for every other actor.

What is not covered:

- Mailbox meters and `actor.lifecycle.active` count actors on the default mailbox. An actor spawned with a
  bounded or a configured mailbox keeps that mailbox and has only the `actor.message.*` and restart meters.
- Dispatcher meters cover the default and the blocking dispatcher (and dispatchers that inherit their
  executor) on Pekko's fork-join or thread-pool executor. A dispatcher on a Spring `Executor` bean is
  measured by Spring Boot's own executor metrics; one on virtual threads has no pool to measure.
- A behavior from `SpringActorBehavior.wrap` (a raw Pekko behavior) is metered outside its own supervision,
  so its failures and restarts are not seen.

### Overhead

`ActorMetersBenchmark` (`./gradlew :actor-autoconfigure:actorMetersBenchmark`) sends bursts of 1,000,000
tells to one actor in two actor systems side by side, one with the meters off and one with them on,
alternating rounds. On an Apple M5 laptop with Java 21 and a `SimpleMeterRegistry` (the machine was busy
with other builds, so treat the numbers as rough):

| | ns per message (median of 15 rounds) |
|---|---|
| meters off | 126 |
| meters on | 379 |
| overhead | about 250 |

The cost per message is two timer updates (processing time and time in mailbox), four `System.nanoTime()`
calls and one small object in the mailbox; `actor.message.processed` reads the processing timer's count and
costs nothing. An actor that does real work (microseconds per message) pays a small fraction of that; one
that only forwards messages pays about three times the bare cost. Run the benchmark on your hardware before
relying on these numbers, and set `spring.actor.metrics.enabled=false` where the overhead matters more than
the meters.

## Cluster and sharding meters

With `spring.actor.provider=cluster`, these meters are added too.

| Meter | Type | Tags | What |
|---|---|---|---|
| `actor.cluster.members` | Gauge | none | Members this node sees |
| `actor.cluster.unreachable` | Gauge | none | Of them, how many are unreachable |
| `actor.cluster.leader` | Gauge | none | `1` while this node is the leader, else `0` |
| `actor.cluster.singleton.host` | Gauge | none | `1` while this node runs the cluster singletons (it is the oldest member that is up), else `0` |
| `actor.cluster.events` | Counter | `event` | Cluster events this node received after the application started: `MemberUp`, `MemberRemoved`, `UnreachableMember` and the other cluster event types. Every type has a series from the start, so a dashboard sees `0`, not a gap |
| `actor.sharding.regions` | Gauge | `type` | Shard regions that host the entity type in the cluster |
| `actor.sharding.shards` | Gauge | `type` | The entity type's shards on this node |
| `actor.sharding.entities` | Gauge | `type` | The entity type's running entities on this node |

The `actor.sharding.*` gauges ask every shard region in the cluster, so a scrape never waits for the
answer: each read shows the answer to the previous read. Sum `actor.sharding.entities` over the nodes for
the cluster total.

Pekko reports no rebalance events, so there is no rebalance counter. A shard that moves shows up as
`actor.sharding.shards` going down on one node and up on another.

Alert on `actor.cluster.unreachable > 0` for longer than the split brain resolver's `stable-after`, and on
a `members` count below the replica count. The [alert rules](#dashboard-and-alerts) do the first.

### Prometheus names

Micrometer's Prometheus registry turns the dots into underscores and adds a suffix per type:

| Meter type | Example | Prometheus series |
|---|---|---|
| Counter | `actor.message.processed` | `actor_message_processed_total` |
| Timer | `actor.mailbox.time` | `actor_mailbox_time_seconds_count`, `_seconds_sum`, `_seconds_max` (and `_seconds_bucket` with histograms on) |
| Gauge | `actor.cluster.members` | `actor_cluster_members` |

Tags become labels the same way: `actor.class` is `actor_class`.

## The `actors` endpoint

With Actuator on the classpath, cluster mode adds a read-only `actors` endpoint. Like every Actuator
endpoint except `health`, it is not exposed over HTTP until you name it:

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,actors
```

`GET /actuator/actors` returns:

- `node`: this node's address.
- `cluster`: this node's view of the cluster: its own status (`selfStatus`), whether it is unreachable or
  terminated, the member and unreachable counts, whether it is the `leader` and the `singletonHost`, and
  `nodes`, each member oldest first with its `address`, `status`, `roles` and whether it is `reachable`.
- `sharding`: for each sharded entity type, its `type`, the `regions` in the cluster, and the `shards` and
  `entities` on this node.

A read asks every shard region, so it can take up to 3 seconds.
`management.endpoint.actors.access=none` turns the endpoint off.

## Health

The `actorSystem` and `actorCluster` health components are described in
[Running in Production](production.md#health-checks).

## Tracing

With an `ObservationRegistry` bean (Spring Boot Actuator creates one) and a tracer such as
`micrometer-tracing-bridge-otel` or `micrometer-tracing-bridge-brave`, the trace of the current request
crosses `tell` and `ask`. Without an `ObservationRegistry`, messages are sent as they are and nothing is
added.

- Sending a message in an observation (an HTTP request, another actor's message) creates an `actor.send`
  span (`tell Order`, `ask GetBalance`). For `ask` it ends when the reply or the timeout arrives.
- The receiving actor handles the message inside an `actor.receive` span, a child of the send. Its log
  lines carry `traceId` and `spanId` in the MDC, as Spring Boot's tracing sets them.
- Between nodes, sharded messages and messages to remote actors carry the W3C `traceparent` fields next to
  the message, which keeps its own serializer (`JsonSerializable`, `CborSerializable`).
- Spans carry the low-cardinality tags `actor.operation` and `actor.message` (the message class); the
  receive span also has the actor path as a high-cardinality tag.

The trace is the observation current on the sending thread. A `tell` or `ask` from a `CompletionStage`
callback (`getOrSpawn().thenCompose(actor -> actor.ask(...))`) runs on an actor thread with no observation,
so it starts no span. Get the handle first, then send from the request's thread, or carry the context into
the callback with Micrometer's `ContextSnapshot`. In Kotlin, `awaitGetOrSpawn` followed by `awaitAsk` in a
`suspend` function avoids the callback.

Messages that `ask` replies are not traced: the caller is already in the trace. Messages sent through a
plain `SpringActorRef` (a `replyTo`, a ref inside a message) are not traced either; handles from
`SpringActorSystem`, `getSelf()`, children, the receptionist and sharding are.

## Logs

Actor log lines carry MDC values: the actor path and system, any static or per-message MDC you add, actor
tags, and with tracing, `traceId` and `spanId`. [Logging with MDC and Tags](../core/logging.md) shows how to
add them and how to print them with Logback, also as JSON.

## Dashboard and alerts

The repository's [`monitoring/`](https://github.com/seonWKim/actor-spring-boot-starter/tree/main/monitoring)
directory has a Grafana dashboard and Prometheus alert rules for the meters on this page, under their
[Prometheus names](#prometheus-names). A test (`MonitoringFilesTest`) checks that every metric they query is
a meter described on this page.


### The Actors dashboard

`monitoring/grafana/actor-dashboard.json`. [Set up Grafana](#set-up-grafana) loads it; you can also import it
by hand and pick your Prometheus data source. The variables `instance` and `system` filter by instance and actor system.

| Row | Panels |
|---|---|
| Messages | Messages processed per second, message failures per second, average and longest processing time (per actor class) |
| Mailboxes and actors | Messages waiting in mailboxes, average time in mailbox, running actors, restarts |
| Dispatchers | Pool threads, tasks waiting for a thread |
| Cluster | Members, unreachable, leader, singleton host, members by node, cluster events |
| Sharding | Entities by type, shards by node, shard regions |

### Alert rules

`monitoring/prometheus/actor-alerts.yaml` is a `PrometheusRule` for the Prometheus Operator. Without the
operator, its `spec` is a plain rules file:

```bash
yq '.spec' monitoring/prometheus/actor-alerts.yaml > actor-alerts.rules.yml
```

| Alert | Fires when | Severity |
|---|---|---|
| `ActorClusterMemberUnreachable` | a member is unreachable for 5 minutes | warning |
| `ActorClusterNoLeader` | no node is the leader for 5 minutes | critical |
| `ActorClusterViewsDiffer` | nodes see different member counts for 10 minutes | warning |
| `ActorClusterSingletonMissing` | no node runs the singletons for 5 minutes | critical |
| `ActorMessageFailures` | an actor class keeps failing messages for 10 minutes | warning |
| `ActorRestartsFrequent` | an actor class restarts more than 10 times in 15 minutes | warning |
| `ActorMailboxBacklog` | more than 1,000 messages wait for an actor class on one instance for 10 minutes | warning |
| `ActorMailboxWaitHigh` | messages wait more than a second on average for 10 minutes | warning |
| `ActorDispatcherSaturated` | every thread of a dispatcher is busy and more than 100 tasks wait for 10 minutes | warning |

The thresholds are starting points; tune them to your load. The rules group by `namespace`, the label the
Prometheus Operator adds; with another setup, change it to the label that tells your applications apart.

### Set up Grafana

#### Download the files for your version

Use the files of the release you depend on: the dashboard on `main` may query meters a later release added.
Each GitHub release attaches them, with the version in their names:

```text
https://github.com/seonWKim/actor-spring-boot-starter/releases/download/v<version>/actor-grafana-dashboard-<version>.json
https://github.com/seonWKim/actor-spring-boot-starter/releases/download/v<version>/actor-prometheus-alerts-<version>.yaml
https://github.com/seonWKim/actor-spring-boot-starter/releases/download/v<version>/actor-monitoring-<version>.zip
```

The dashboard carries the version as a `v<version>` tag and at the end of its description. The zip holds the
`monitoring/` directory for that version: the dashboard, the alert rules, the provisioning files and `docker-compose.yml`.

#### Provisioning

To load the dashboard into your own Grafana without clicks, copy the two provisioning files into Grafana's
provisioning directory (`/etc/grafana/provisioning` by default) and the dashboard into the directory the
dashboard provider reads:

| File | Goes to | Change |
|---|---|---|
| `monitoring/grafana/provisioning/datasources/prometheus.yaml` | `provisioning/datasources/` | `url`: your Prometheus. Skip the file if Grafana already has a default Prometheus data source |
| `monitoring/grafana/provisioning/dashboards/actor.yaml` | `provisioning/dashboards/` | `options.path` if you keep dashboards elsewhere |
| `monitoring/grafana/actor-dashboard.json` | `/var/lib/grafana/dashboards/actor/` (the provider's `options.path`) | |

The dashboard's **Data source** variable starts on Grafana's default Prometheus data source.

#### Run it locally

With your application running on port 8080 and exposing `/actuator/prometheus`:

```bash
cd monitoring                     # or the unzipped actor-monitoring-<version>/
docker compose up -d
```

- Grafana: <http://localhost:3000> (`admin` / `admin`), dashboard **Actors** in the **Actors** folder.
- Prometheus: <http://localhost:9090>, the alert rules under *Alerts*, the scrape target under *Status > Targets*.

Prometheus scrapes `host.docker.internal:8080/actuator/prometheus`. Set `ACTOR_TARGET` for another address:

```bash
ACTOR_TARGET=host.docker.internal:9000 docker compose up -d
docker compose down -v            # stop and remove the containers and the generated files
```

The `setup` service writes the scrape target and turns `actor-alerts.yaml` into a plain rules file
(its `spec`) before Prometheus starts. The cluster alerts group by `namespace`, which a local stack doesn't
have, so they treat everything Prometheus scrapes as one group.

### Installing on Kubernetes

With the Prometheus Operator in the cluster (for example kube-prometheus-stack), the Kubernetes example
installs both in one command:

```bash
cd example/kubernetes
./scripts/monitoring.sh          # apply; `render` prints the manifests, `delete` removes them
```

It applies a ServiceMonitor that scrapes `/actuator/prometheus`, the `actor-alerts` PrometheusRule, and the
dashboard as a ConfigMap labelled `grafana_dashboard: "1"`, which Grafana's dashboard sidecar loads. Your
Prometheus must select them: kube-prometheus-stack selects rules and monitors by its release label unless
you set `prometheus.prometheusSpec.ruleSelectorNilUsesHelmValues=false` and
`serviceMonitorSelectorNilUsesHelmValues=false`, and its sidecar searches other namespaces only with
`grafana.sidecar.dashboards.searchNamespace=ALL`.

## The optional Java agent

The ByteBuddy agent in `actor-metrics` instruments Pekko internals for a few meters no public hook exposes
(stash size, dead letters, unhandled messages, scheduled tasks, per-message-type tags). It breaks more easily
with Pekko upgrades, so use it only when you need those.

```bash
./gradlew :actor-metrics:agentJar

java -javaagent:actor-metrics-{version}-agent.jar -jar your-app.jar
```

With the agent attached, it records the message, mailbox and lifecycle meters below under the same names,
and the hooks register only `actor.message.failures` and the `actor.dispatcher.*` meters: no meter is
registered twice.

### Agent meters

| Meter | Type | Tags | What |
|---|---|---|---|
| `actor.lifecycle.created` | Counter | `actor.class` | Actors created |
| `actor.lifecycle.terminated` | Counter | `actor.class` | Actors terminated |
| `actor.lifecycle.restarts` | Counter | `actor.class` | Supervision restarts |
| `actor.lifecycle.resumes` | Counter | `actor.class` | Supervision resumes |
| `actor.lifecycle.active` | Gauge | none | Currently active actors |
| `actor.mailbox.size` | Gauge | `actor.class` | Mailbox queue size |
| `actor.mailbox.size.max` | Gauge | `actor.class` | Peak mailbox size |
| `actor.mailbox.time` | Timer | `actor.class`, `message.type` | Time in mailbox |
| `actor.mailbox.overflow` | Counter | none | Bounded mailbox drops |
| `actor.message.processed` | Counter | `actor.class`, `message.type` | Messages processed |
| `actor.message.processing.time` | Timer | `actor.class`, `message.type` | Processing duration |
| `actor.stash.size` | Gauge | `actor.class` | Stashed messages |
| `system.dead-letters` | Counter | none | Undeliverable messages |
| `system.unhandled-messages` | Counter | none | Unhandled messages |
| `scheduler.tasks.scheduled` | Counter | none | Tasks via `scheduleOnce` |

Typed actors use `actor.class=TypedActor`; use `message.type` to tell behaviors apart. The agent records
no failure meter of its own: failures are `actor.message.failures`, which the hooks record with or without
the agent.

### Agent settings

The agent reads environment variables, not Spring keys: `ACTOR_METRICS_ENABLED`,
`ACTOR_METRICS_INSTRUMENT_<MODULE>`, `ACTOR_METRICS_TAG_<NAME>` and `ACTOR_METRICS_SAMPLING_RATE`. The
[Configuration Reference](configuration.md#metrics-agent-environment-variables-actor_metrics_) describes
each. Module ids: `actor-lifecycle`, `mailbox`, `message-processing`, `system`, `scheduler`, `stash`.

To set tags or sampling in code, declare your own `MetricsRegistry` bean:

=== "Java"

    ```java
    import io.github.seonwkim.actor.metrics.core.MetricsConfiguration.SamplingConfig;
    import io.github.seonwkim.actor.metrics.core.MetricsRegistry;
    import io.github.seonwkim.actor.metrics.micrometer.MicrometerMetricsRegistryBuilder;

    @Configuration
    public class ActorMetricsConfig {

        @Bean
        public MetricsRegistry actorMetricsRegistry(MeterRegistry meterRegistry) {
            return MicrometerMetricsRegistryBuilder.fromEnvironment(meterRegistry)
                    .tag("custom-tag", "value")
                    .sampling(SamplingConfig.rateBased(0.1))
                    .build();
        }
    }
    ```

=== "Kotlin"

    ```kotlin
    import io.github.seonwkim.actor.metrics.core.MetricsConfiguration.SamplingConfig
    import io.github.seonwkim.actor.metrics.core.MetricsRegistry
    import io.github.seonwkim.actor.metrics.micrometer.MicrometerMetricsRegistryBuilder

    @Configuration
    class ActorMetricsConfig {

        @Bean
        fun actorMetricsRegistry(meterRegistry: MeterRegistry): MetricsRegistry =
            MicrometerMetricsRegistryBuilder.fromEnvironment(meterRegistry)
                .tag("custom-tag", "value")
                .sampling(SamplingConfig.rateBased(0.1))
                .build()
    }
    ```

### How the agent starts

The agent must rewrite bytecode before Pekko classes load, but Micrometer only exists after Spring starts.
So it starts in two steps:

1. **JVM startup:** `MetricsAgent.premain()` finds the modules through `ServiceLoader` and uses ByteBuddy to
   instrument Pekko classes. No meters are recorded yet.
2. **Spring context:** the auto-configuration creates a `MetricsRegistry` with a Micrometer backend and
   hands it to the agent. Meters start recording. The registry is created before the actor system bean.

## Troubleshooting

| Problem | What to check |
|---|---|
| `/actuator/prometheus` is 404 | `prometheus` is in `management.endpoints.web.exposure.include` and `micrometer-registry-prometheus` is on the classpath. |
| No `actor_*` series | `actor-metrics` is on the classpath and `spring.actor.metrics.enabled` is not `false`. Counters appear after the first message. |
| No `actor_cluster_*` series | `spring.actor.provider` is `cluster`. |
| No agent meters | The log has `[MetricsAgent] Metrics agent installed successfully`, and `-javaagent` is on the command line. |
| Agent jar missing | Run `./gradlew :actor-metrics:agentJar`. |

## Related

- [Running in Production](production.md): health checks and graceful shutdown
- [Configuration Reference](configuration.md#metrics): the metrics keys
- [Logging with MDC and Tags](../core/logging.md)
- [Metrics example](../../examples/metrics.md) and [Kubernetes example](../../examples/kubernetes.md)

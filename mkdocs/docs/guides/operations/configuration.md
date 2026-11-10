# Configuration Reference

This page lists every configuration key the starter and its modules read, with its default and what it does.

The keys are published as Spring Boot configuration metadata
(`META-INF/spring-configuration-metadata.json` in the `actor-autoconfigure` and `actor-metrics` jars), so
IDEs offer completion and documentation for them in `application.yml` and `application.properties`.

There are three kinds of settings:

- **Typed keys** (`spring.actor.*`, `management.*`): bound to a properties class and checked at startup.
- **Raw Pekko keys** (any other key under `spring.actor.*`): passed to Pekko as they are.
- **Environment variables** (`ACTOR_METRICS_*`): read only by the optional metrics Java agent.

## Where the keys live

All typed keys are bound by three `@ConfigurationProperties` classes. The store and persistence modules
(`actor-jdbc`, `actor-redis`, `actor-persistence`, `actor-projection`) have no properties class of their
own: their keys are part of `ActorProperties`, and they take their connection from the application's
`DataSource` or `RedisConnectionFactory`.

| Class | Prefix | Module |
|---|---|---|
| `ActorProperties` | `spring.actor` | `actor-autoconfigure` (in every starter) |
| `PekkoManagementProperties` | `spring.actor.pekko.management` | `actor-autoconfigure` |
| `ActorMetricsProperties` | `spring.actor.metrics` | `actor-metrics` |

Typed keys are validated when the application starts. A bad value stops it with a message that names
the key, for example `spring.actor.remote.port must be between 0 and 65535, got 70000`.

## Actor system

| Key | Default | Sets Pekko key | Description |
|---|---|---|---|
| `spring.actor.name` | `system` | `pekko.name` | Name of the actor system. Letters, digits, `-` and `_`, starting with a letter or digit. Nodes of one cluster must use the same name. |
| `spring.actor.provider` | `local` | `pekko.actor.provider` | How actors reach each other: `local` (one JVM), `remote` (reachable from other nodes, no cluster membership) or `cluster`. `remote` and `cluster` need `actor-spring-boot-starter-cluster`. |

## Remoting

Used with `provider: remote` or `provider: cluster`.

| Key | Default | Sets Pekko key | Description |
|---|---|---|---|
| `spring.actor.remote.hostname` | Pekko's default | `pekko.remote.artery.canonical.hostname` | Host name or address other nodes use to reach this node. |
| `spring.actor.remote.port` | Pekko's default (`25520`) | `pekko.remote.artery.canonical.port` | Port this node listens on for other nodes, `0` to `65535`. `0` picks a free port. |

## Cluster

Used with `provider: cluster`. See [Cluster Formation](../cluster/cluster-formation.md) and
[Split Brain and Leases](../cluster/split-brain-and-leases.md).

### Joining

| Key | Default | Sets Pekko key | Description |
|---|---|---|---|
| `spring.actor.cluster.join` | `seed-nodes` | none | How this node finds the cluster: `seed-nodes` (the listed nodes) or `discovery` (peers found through `discovery.method`, with Pekko Cluster Bootstrap). |
| `spring.actor.cluster.seed-nodes` | none | `pekko.cluster.seed-nodes` | Nodes to join at startup, as `pekko://<name>@<host>:<port>`. Not with `join: discovery`. |
| `spring.actor.cluster.join-self` | `false` | none | Joins this node to itself: a one-node cluster without seed nodes, also on port `0`. For local runs and tests. Not with `seed-nodes` or `join: discovery`. |
| `spring.actor.cluster.roles` | none | `pekko.cluster.roles` | Roles of this node. Sharded actors and singletons can be limited to a role. |

### Discovery

Used with `join: discovery`.

| Key | Default | Sets Pekko key | Description |
|---|---|---|---|
| `spring.actor.cluster.discovery.method` | none | `pekko.discovery.method` | Where peers are found: `jdbc` (needs `actor-jdbc`), `redis` (needs `actor-redis`), `custom` (your `PeerRegistry` bean), `kubernetes-api`, `dns` or `config`. Required with `join: discovery`. A `PeerRegistry` bean of your own replaces the `jdbc` and `redis` stores. |
| `spring.actor.cluster.discovery.heartbeat-interval` | `5s` | none | How often a node renews its own entry (`jdbc`, `redis`, `custom`). Must be positive. |
| `spring.actor.cluster.discovery.ttl` | `30s` | none | How long an entry counts as alive after its last renewal (`jdbc`, `redis`, `custom`). Must be positive. |

### Downing (split brain resolver)

| Key | Default | Sets Pekko key | Description |
|---|---|---|---|
| `spring.actor.cluster.downing.strategy` | `keep-majority` with `join: discovery`, else `none` | `pekko.cluster.downing-provider-class`, `pekko.cluster.split-brain-resolver.active-strategy` | Which side of a network split stays up: `keep-majority`, `lease-majority`, `static-quorum`, `keep-oldest`, `down-all` or `none`. A cluster without downing logs a warning. |
| `spring.actor.cluster.downing.quorum-size` | none | `pekko.cluster.split-brain-resolver.static-quorum.quorum-size` | Nodes a side needs to stay up. Required with `static-quorum`; at least `1`. Set it above half the cluster's size. |

### Leases

| Key | Default | Sets Pekko key | Description |
|---|---|---|---|
| `spring.actor.cluster.lease.store` | none | none | Where leases live: `jdbc` (needs `actor-jdbc`), `redis` (needs `actor-redis`) or `custom` (your `LeaseStore` bean). Required by `downing.strategy: lease-majority`, `singleton.use-lease` and `sharding.use-lease`. |
| `spring.actor.cluster.lease.ttl` | `120s` | `spring-actor.lease.heartbeat-timeout` | How long a lease stays held after its last renewal before another node may take it. |
| `spring.actor.cluster.lease.renew-interval` | `12s` | `spring-actor.lease.heartbeat-interval` | How often the holder renews a lease. |
| `spring.actor.cluster.lease.expiry-margin` | `10s` | `spring-actor.lease.expiry-margin` | The holder treats its lease as lost this long before the ttl ends, so it stops before another node can take the lease. Must be less than `ttl`. |
| `spring.actor.cluster.singleton.role` | none | `pekko.cluster.singleton.role` | Cluster singletons run only on nodes with this role. Set the same value on every node. |
| `spring.actor.cluster.singleton.use-lease` | `false` | `pekko.cluster.singleton.use-lease` | A cluster singleton runs only while it holds a lease, so two nodes never run it at once, even during a split. |
| `spring.actor.cluster.sharding.use-lease` | `false` | `pekko.cluster.sharding.use-lease` | Each shard runs only while it holds a lease. |

### Pekko Management

| Key | Default | Description |
|---|---|---|
| `spring.actor.pekko.management.enabled` | `true` | Whether to start the Pekko Management HTTP server and Cluster Bootstrap in cluster mode, when the Pekko Management libraries are on the classpath. The server's own settings (`spring.actor.pekko.management.http.port`, ...) are raw Pekko keys. |

## Database tables

| Key | Default | Description |
|---|---|---|
| `spring.actor.jdbc.initialize-schema` | `embedded` | When to create the `actor-jdbc` tables (`actor_peer`, `actor_lease`): `embedded` (only in an embedded database), `always`, or `never` (you create them from the `schema-*.sql` files in `actor-jdbc`). |
| `spring.actor.persistence.initialize-schema` | `embedded` | When to create the `actor-persistence` tables (`event_journal`, `event_tag`, `snapshot`) and the `actor-projection` offset tables: `embedded`, `always` or `never`. See [Event-Sourced Actors](../data/event-sourcing.md#tables) and [Projections](../data/projections.md). |

## Dispatchers and threads

See [Dispatchers](../core/dispatchers.md).

| Key | Default | Sets Pekko key | Description |
|---|---|---|---|
| `spring.actor.dispatchers.<name>.executor` | none | `<name>.*` | Declares a dispatcher that runs on the named Spring `Executor` bean (for example a `ThreadPoolTaskExecutor`). Actors pick it with `withDispatcherFromConfig("<name>")`. |
| `spring.actor.dispatchers.<name>.virtual` | `false` | `<name>.executor` | Declares a dispatcher that runs on virtual threads, one per task. Set either `executor` or `virtual: true`, not both. |
| `spring.actor.dispatchers.<name>.throughput` | Pekko's default (`5`) | `<name>.throughput` | Messages an actor processes before its thread moves to the next actor. At least `1`. |
| `spring.actor.virtual-threads.enabled` | `auto` | `pekko.actor.default-blocking-io-dispatcher.executor` | Runs the blocking dispatcher (`withBlockingDispatcher()`) on virtual threads: `auto`, `true` or `false`. `auto` follows `spring.threads.virtual.enabled`. The default dispatcher always stays on platform threads. |

A dispatcher name is letters, digits, `-` and `_`, and must not be `pekko` or the name of a typed key
(`name`, `cluster`, `dispatchers`, ...).

## Shutdown

See [Graceful shutdown](production.md#graceful-shutdown).

| Key | Default | Description |
|---|---|---|
| `spring.actor.shutdown.timeout` | `20s` | How long closing the Spring context waits for the actor system's coordinated shutdown. Must be positive. |
| `spring.actor.shutdown.exit-process` | `true` | When the actor system terminates by itself (for example after this node is downed), the context closes; with this set, the process then exits with status 1 so an orchestrator restarts it. |

## Metrics

Needs `actor-metrics` and a Micrometer `MeterRegistry`. See [Monitoring](monitoring.md).

| Key | Default | Description |
|---|---|---|
| `spring.actor.metrics.enabled` | `true` | Whether to register the actor meters, the actor `MetricsRegistry` bean and, in cluster mode, the `actor.cluster.*` and `actor.sharding.*` meters. |
| `spring.actor.metrics.path-tag-limit` | `0` | How many actor paths get their own `actor.path` tag on the `actor.message.*` meters; the rest share `actor.path=other`. `0` adds no `actor.path` tag, so the series count never grows with the number of actors. |

## Actuator

Need Spring Boot Actuator. See [Health checks](production.md#health-checks) and
[The actors endpoint](monitoring.md#the-actors-endpoint).

| Key | Default | Description |
|---|---|---|
| `management.health.actor.enabled` | `true` | Whether to add the `actorSystem` and (in cluster mode) `actorCluster` health components. |
| `management.endpoint.actors.access` | `unrestricted` | Access to the `actors` endpoint (cluster mode): `unrestricted`, `read-only` or `none`. `none` turns it off. Spring Boot adds this key for every endpoint. |
| `management.endpoint.actors.cache.time-to-live` | `0ms` | How long a response of the `actors` endpoint may be cached. Spring Boot adds this key for every endpoint. |

`management.endpoint.actors.enabled` also appears in the metadata. Spring Boot generates it for every
endpoint and marks it deprecated; use `access` instead.

## Spring Boot keys that matter

These are Spring Boot's own keys. The starter reads or depends on them:

| Key | Why |
|---|---|
| `spring.threads.virtual.enabled` | `spring.actor.virtual-threads.enabled: auto` follows it. |
| `server.shutdown: graceful` and `spring.lifecycle.timeout-per-shutdown-phase` | The web server drains requests before the actor system stops. See [Shutdown order](production.md#shutdown-order). |
| `management.endpoints.web.exposure.include` | The `actors` and `prometheus` endpoints are exposed over HTTP only when listed here. |
| `management.endpoint.health.group.readiness.include` | Add `actorCluster` to gate traffic on cluster membership. |

## Example

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
      downing:
        strategy: keep-majority
    shutdown:
      timeout: 25s
```

## Raw Pekko keys (`spring.actor.*`)

Every other key under `spring.actor.*` is passed unchanged to the Pekko actor system configuration,
with the `spring.actor` prefix removed. For example, `spring.actor.pekko.loglevel` becomes
`pekko.loglevel`, and a dispatcher defined at `spring.actor.my-dispatcher` becomes `my-dispatcher`.
Comma-separated string values are turned into lists, and a single value for a key that Pekko reads as
a list is a one-item list: `spring.actor.pekko.cluster.seed-nodes=pekko://orders@10.0.0.7:25521` works
like `seed-nodes[0]`. See the
[Pekko reference configuration](https://pekko.apache.org/docs/pekko/current/general/configuration-reference.html)
for the available keys.

```yaml
spring:
  actor:
    pekko:
      loglevel: INFO
      coordinated-shutdown:
        phases:
          cluster-leave:
            timeout: 10s
```

!!! note "Raw keys win"
    When a raw key and a typed key set the same Pekko key to different values, the raw key wins and
    the starter logs a warning that names both, for example
    `spring.actor.pekko.name=raw overrides spring.actor.name=typed`. Prefer the typed key.

## Metrics agent environment variables (`ACTOR_METRICS_*`)

!!! warning "Not Spring keys"
    These are environment variables, read by the optional metrics Java agent and by the metrics registry
    builder with `System.getenv` (falling back to a JVM system property of the same name). They are
    not Spring configuration keys: setting them in `application.yml` or `application.properties`
    has no effect, and they do not appear in the Spring configuration metadata.

| Variable | Default | Description |
|---|---|---|
| `ACTOR_METRICS_ENABLED` | `true` | Read by the agent at JVM startup: `false` skips all instrumentation. Also read by the metrics registry builder to set the enabled flag of the metrics configuration. |
| `ACTOR_METRICS_INSTRUMENT_<MODULE>` | `true` | Read by the agent: `false` skips instrumentation for one module. `<MODULE>` is the module id in upper case with `-` replaced by `_`: `ACTOR_LIFECYCLE`, `MESSAGE_PROCESSING`, `MAILBOX`, `SYSTEM`, `SCHEDULER`, `STASH`. |
| `ACTOR_METRICS_TAG_<NAME>` | none | Adds a global tag to every agent metric. The tag name is `<NAME>` in lower case with `_` replaced by `.`; for example `ACTOR_METRICS_TAG_APPLICATION=my-app` adds the tag `application=my-app`. |
| `ACTOR_METRICS_SAMPLING_RATE` | `1.0` | Sampling rate between `0.0` and `1.0`. |

```bash
ACTOR_METRICS_TAG_APPLICATION=my-app \
ACTOR_METRICS_INSTRUMENT_MAILBOX=false \
java -javaagent:actor-metrics-{version}-agent.jar -jar app.jar
```

## Related

- [Running in Production](production.md): shutdown and health settings in context
- [Monitoring](monitoring.md): what the metrics keys turn on
- [Cluster Formation](../cluster/cluster-formation.md), [Split Brain and Leases](../cluster/split-brain-and-leases.md)
- [Dispatchers](../core/dispatchers.md)

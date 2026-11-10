# Cluster Singletons

A cluster singleton is an actor that runs on exactly one node of the cluster. Every node can send it
messages through a proxy, and when the node that runs it leaves or fails, the singleton starts again on
another node.

Use a singleton for work that must happen once for the whole cluster:

- a coordinator that hands out work or ids,
- a scheduler that runs a job every few minutes,
- an aggregator that collects numbers from every node,
- a cluster-wide limit, such as a rate limiter shared by all nodes.

Don't use one for work that grows with your traffic. A singleton is one actor on one node: every message
for it goes through that node, one at a time. For many independent things (users, orders, devices), use
[sharded actors](sharded-actors.md) instead: one entity per id, spread over the nodes.

## Setup

Singletons need the cluster starter and cluster mode:

=== "Gradle"

    ```kotlin
    implementation("io.github.seonwkim:actor-spring-boot-starter-cluster:$version")
    ```

=== "Maven"

    ```xml
    <dependency>
        <groupId>io.github.seonwkim</groupId>
        <artifactId>actor-spring-boot-starter-cluster</artifactId>
        <version>${actor.version}</version>
    </dependency>
    ```

```yaml
spring:
  actor:
    name: orders
    provider: cluster
    remote:
      hostname: 127.0.0.1
      port: 2551
    cluster:
      seed-nodes:
        - pekko://orders@127.0.0.1:2551
        - pekko://orders@127.0.0.1:2552
      downing:
        strategy: keep-majority
```

See [Cluster Formation](cluster-formation.md) for how nodes find each other, and
[Split Brain and Leases](split-brain-and-leases.md) for the downing strategy.

## Write the actor

A singleton is a regular actor. Nothing in the class says it is a singleton: you choose that when you
spawn it. Its messages cross the network, so they must be serializable (`JsonSerializable` or
`CborSerializable`), the same as for sharded actors.

This one is adapted from the
[cluster example](https://github.com/seonWKim/actor-spring-boot-starter/tree/main/example/cluster):

=== "Java"

    ```java
    @Component
    public class ClusterMetricsAggregator implements SpringActor<ClusterMetricsAggregator.Command> {

        public interface Command extends JsonSerializable {}

        public static class RecordMetric implements Command {
            public final String nodeAddress;
            public final String metricName;
            public final long value;

            public RecordMetric(String nodeAddress, String metricName, long value) {
                this.nodeAddress = nodeAddress;
                this.metricName = metricName;
                this.value = value;
            }
        }

        public static class GetTotal extends AskCommand<Long> implements Command {
            public final String metricName;

            public GetTotal(String metricName) {
                this.metricName = metricName;
            }
        }

        @Override
        public SpringActorBehavior<Command> create(SpringActorContext actorContext) {
            Map<String, Long> totals = new HashMap<>();
            return SpringActorBehavior.builder(Command.class, actorContext)
                    .onMessage(RecordMetric.class, (ctx, msg) -> {
                        totals.merge(msg.metricName, msg.value, Long::sum);
                        return SpringActorBehavior.same();
                    })
                    .onMessage(GetTotal.class, (ctx, msg) -> {
                        msg.reply(totals.getOrDefault(msg.metricName, 0L));
                        return SpringActorBehavior.same();
                    })
                    .build();
        }
    }
    ```

=== "Kotlin"

    ```kotlin
    @Component
    class ClusterMetricsAggregator : SpringActor<ClusterMetricsAggregator.Command> {

        sealed interface Command : JsonSerializable

        class RecordMetric(val nodeAddress: String, val metricName: String, val value: Long) : Command

        class GetTotal(val metricName: String) : AskCommand<Long>(), Command

        override fun create(actorContext: SpringActorContext): SpringActorBehavior<Command> {
            val totals = mutableMapOf<String, Long>()
            return actorBehavior<Command>(actorContext)
                .onMessage(RecordMetric::class.java) { _, msg ->
                    totals.merge(msg.metricName, msg.value, Long::plus)
                    SpringActorBehavior.same()
                }
                .onMessage(GetTotal::class.java) { _, msg ->
                    msg.reply(totals.getOrDefault(msg.metricName, 0L))
                    SpringActorBehavior.same()
                }
                .build()
        }
    }
    ```

## Spawn it

Call `asClusterSingleton()` on the spawn builder:

=== "Java"

    ```java
    SpringActorHandle<ClusterMetricsAggregator.Command> aggregator = actorSystem
            .actor(ClusterMetricsAggregator.class)
            .withId("metrics-aggregator")
            .asClusterSingleton()
            .spawn()
            .toCompletableFuture()
            .get();
    ```

=== "Kotlin"

    ```kotlin
    // In a suspend fun or a coroutine
    val aggregator: SpringActorHandle<ClusterMetricsAggregator.Command> = actorSystem
        .actor<ClusterMetricsAggregator, _>()
        .withId("metrics-aggregator")
        .asClusterSingleton()
        .awaitSpawn()
    ```

`asClusterSingleton(boolean)` does the same with a flag, for code that decides at run time.

What you get back is a handle to a **proxy** on this node, not to the actor itself. The proxy finds the
node that runs the singleton and forwards every message there. `tell` and `ask` work as on any handle.

Every node spawns the singleton with the same class and id. The class name and the id form the singleton's
name (`<class name>:<id>`), and nodes that use the same name share one singleton. Spawning it again, on the
same node or another one, is safe: it returns a proxy to the same singleton and does not start a second
one.

The usual place is a bean that spawns it once at startup and keeps the handle:

=== "Java"

    ```java
    @Service
    public class ClusterSingletonService {

        private final SpringActorSystem actorSystem;
        private SpringActorHandle<ClusterMetricsAggregator.Command> aggregator;

        public ClusterSingletonService(SpringActorSystem actorSystem) {
            this.actorSystem = actorSystem;
        }

        @PostConstruct
        public void init() throws Exception {
            aggregator = actorSystem
                    .actor(ClusterMetricsAggregator.class)
                    .withId("metrics-aggregator")
                    .asClusterSingleton()
                    .spawn()
                    .toCompletableFuture()
                    .get();
        }

        public void record(String name, long value) {
            aggregator.tell(new ClusterMetricsAggregator.RecordMetric(actorSystem.nodeAddress(), name, value));
        }

        public CompletionStage<Long> total(String name) {
            return aggregator
                    .ask(new ClusterMetricsAggregator.GetTotal(name))
                    .withTimeout(Duration.ofSeconds(3))
                    .execute();
        }
    }
    ```

=== "Kotlin"

    ```kotlin
    @Service
    class ClusterSingletonService(private val actorSystem: SpringActorSystem) {

        private lateinit var aggregator: SpringActorHandle<ClusterMetricsAggregator.Command>

        @PostConstruct
        fun init() {
            aggregator = actorSystem
                .actor<ClusterMetricsAggregator, _>()
                .withId("metrics-aggregator")
                .asClusterSingleton()
                .spawn()
                .toCompletableFuture()
                .get()
        }

        fun record(name: String, value: Long) {
            aggregator.tell(ClusterMetricsAggregator.RecordMetric(actorSystem.nodeAddress(), name, value))
        }

        suspend fun total(name: String): Long =
            aggregator.awaitAsk(ClusterMetricsAggregator.GetTotal(name), timeout = 3.seconds)
    }
    ```

The proxy stays valid for the life of the node, also when the singleton moves. Keep it and reuse it.

The other spawn settings apply to the singleton: `withSupervisionStrategy`, the dispatcher
(`withBlockingDispatcher()`, `withDispatcherFromConfig(...)`), the mailbox and tags.

!!! warning "`find` and `exists` see only singletons this node spawned"
    `actorSystem.find(...)` and `exists(...)` find a singleton on the nodes that called `spawn()` with
    `asClusterSingleton()` for it. On another node they report no actor: call `spawn()` with
    `asClusterSingleton()` there too (it is idempotent), or `getOrSpawn()`. A child actor can't be a
    singleton: only actors spawned from `SpringActorSystem` can.

In local mode (`spring.actor.provider` not `cluster`), the stage returned by `spawn()` fails with
`IllegalStateException`: "Cluster singleton requested but cluster mode is not enabled. Set
spring.actor.provider=cluster and depend on io.github.seonwkim:actor-spring-boot-starter-cluster."

### Stopping

`stop()` on a singleton's handle throws `UnsupportedOperationException`, and the singleton and the handle
keep working. The handle is this node's proxy: stopping it would not stop the singleton, only cut this
node off from it, and every later `spawn()` on the node would get the same stopped proxy back. A singleton
runs as long as the cluster does.

## Where it runs

The singleton runs on the **oldest member** of the cluster whose status is `Up`: the node that joined
first. The other nodes run only a proxy.

| When | What happens |
|---|---|
| A node joins | Nothing. The oldest node keeps the singleton. |
| The host shuts down gracefully (`SIGTERM`, `context.close()`) | It leaves the cluster, the singleton stops there and starts on the next oldest node. |
| The host crashes or stops answering | The singleton starts on another node only after the host is downed and removed. That needs a downing strategy; without one, the singleton waits until you remove the node by hand. |
| No node is `Up` yet | Messages wait in the proxy until a host is up. |

While the singleton moves, the proxies keep the messages sent to it and deliver them once the new
instance runs. Pekko's proxy keeps up to 1000 messages per node and drops what doesn't fit; set
`spring.actor.pekko.cluster.singleton-proxy.buffer-size` to change it. An `ask` whose reply doesn't come
back in time fails with its timeout, or returns its `onTimeout` value.

### State is lost when it moves

The new instance is a new actor: it starts from `create(...)` with empty state. In the example above, the
totals start again from zero. If the singleton must keep its state, store it:

- load it in `withState(...)` from a table and save it after each change, as in
  [State in a Database](../data/state-in-a-database.md), or
- make the singleton an [event-sourced actor](../data/event-sourcing.md), which rebuilds its state from
  its stored events wherever it starts.

### Limit it to some nodes

To run singletons only on some nodes, give those nodes a role with `spring.actor.cluster.roles` and set
`spring.actor.cluster.singleton.role` on **every** node:

```yaml
# Nodes that may run singletons
spring:
  actor:
    cluster:
      roles: [backend]
      singleton:
        role: backend
```

```yaml
# Other nodes: no role, same singleton.role
spring:
  actor:
    cluster:
      singleton:
        role: backend
```

The singleton then runs on the oldest `Up` node with that role. Every node still gets a proxy, and the
nodes without the role still spawn it, as in [Spawn it](#spawn-it). Set the same `singleton.role` on every
node, so all proxies look for the same host. The role applies to every singleton of the application,
including the ones the starter starts for [projections](../data/projections.md).

| Key | Default | Meaning |
|---|---|---|
| `spring.actor.cluster.singleton.role` | none | Singletons run only on nodes with this role. Sets Pekko's `pekko.cluster.singleton.role`. |

A sharded actor picks its role in code (`role()` on the actor, see
[Sharded Actors](sharded-actors.md#run-entities-on-some-nodes-only)). A singleton's role is one setting for
the node instead, because Pekko places all singletons of a node by the same role and the
`actor.cluster.singleton.host` gauge reports one host for all of them.

## Leases: never two at once

A singleton runs on one node only as long as the nodes agree on who is in the cluster. In a network split,
each side may see itself as the oldest and start its own singleton. The
[downing strategy](split-brain-and-leases.md#downing-strategy) stops one side, but it takes some time.

When two copies would do harm (two schedulers sending the same email, two coordinators handing out the
same id), turn on the singleton lease:

```yaml
spring:
  actor:
    cluster:
      singleton:
        use-lease: true
      lease:
        store: jdbc        # or redis, or custom (your LeaseStore bean)
```

The singleton then starts only after it takes its lease from the store, and stops when it loses it. A node
that can't get the lease keeps retrying, and the singleton does not run on that node meanwhile. Each
singleton has its own lease; its name starts with `<spring.actor.name>-singleton-` and ends with the
singleton's name, such as `orders-singleton-...:metrics-aggregator`. The owner is the node address.

| Key | Default | Meaning |
|---|---|---|
| `spring.actor.cluster.singleton.use-lease` | `false` | A singleton runs only while it holds its lease. |
| `spring.actor.cluster.lease.store` | none | Where leases live: `jdbc`, `redis` or `custom`. Required with `use-lease`. |
| `spring.actor.cluster.lease.ttl` | `120s` | How long a lease stays held after its last renewal. |
| `spring.actor.cluster.lease.renew-interval` | `12s` | How often the holder renews it. |
| `spring.actor.cluster.lease.expiry-margin` | `10s` | The holder stops this long before the ttl runs out. |

The ttl also bounds how long a singleton can stay down after a crash: another node can take the lease only
once the crashed node's lease has expired. Pekko's `spring.actor.pekko.cluster.singleton.lease-retry-interval`
sets how often a node retries a refused lease.

With `store: redis`, a lease can be lost in a Redis failover, and two nodes can hold it for a moment. Use
`jdbc` when a singleton must never run twice. See [Leases](split-brain-and-leases.md#leases) for the stores,
the tables and your own `LeaseStore`.

## See where it runs

With `actor-metrics` and Micrometer, every node reports the gauge `actor.cluster.singleton.host`: `1` on the
node that runs the singletons, `0` on the others. Summed over the nodes it should be `1`. The Prometheus
rules in [`monitoring/`](https://github.com/seonWKim/actor-spring-boot-starter/tree/main/monitoring) alert
with `ActorClusterSingletonMissing` when the sum stays below `1` for 5 minutes.

The gauge means "this node is the oldest `Up` member with the singleton role" (any member when
`singleton.role` is not set). It does not look at the lease: with `use-lease`, the host it names may still
be waiting for its lease.

The `actors` Actuator endpoint (`/actuator/actors`, when exposed) shows the same flag in this node's view of
the cluster.

## Singletons the starter starts

Each [projection](../data/projections.md) runs as a cluster singleton in cluster mode, so one node handles
its events. Its singleton is named `projection-<name>`.

## Pitfalls

- **One node does all the work.** Keep the work a singleton does small, or have it hand work out to
  sharded entities or routers.
- **Blocking calls.** A singleton that calls a database or an HTTP service should run on the blocking
  dispatcher (`withBlockingDispatcher()`), like any blocking actor.
- **No downing strategy.** Without one, a crashed host keeps the singleton until someone removes the node.
  The starter logs a warning at startup when no downing strategy is set.
- **Different ids on different nodes.** Nodes that spawn with another id start another singleton.
- **State in memory.** It is gone when the singleton moves. Store what must survive.

## Related

- [Sharded Actors](sharded-actors.md): one actor per id, spread over the nodes
- [Split Brain and Leases](split-brain-and-leases.md): downing strategies and lease stores
- [Cluster Formation](cluster-formation.md): how nodes join
- [Cluster example](../../examples/cluster.md): a singleton aggregator you can run on three nodes and fail over
- [Monitoring](../operations/monitoring.md): the cluster meters and alerts

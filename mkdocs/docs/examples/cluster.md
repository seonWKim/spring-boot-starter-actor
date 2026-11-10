# Cluster Example

A three-node cluster with two kinds of cluster actors: sharded actors (one instance per id, spread over
the nodes) and a cluster singleton (one instance in the whole cluster).

- **Source:** [`example/cluster`](https://github.com/seonWKim/actor-spring-boot-starter/tree/main/example/cluster)
- **Runs:** three nodes on one machine (`spring.actor.provider: cluster`)
- **Modules:** `actor-spring-boot-starter-cluster`, Spring WebFlux

## What it shows

- A sharded actor (`SpringShardedActor`) addressed by entity id from any node
- A cluster singleton (`asClusterSingleton()`) that collects data sent from every node
- `ask` with `onTimeout` for a fallback answer
- Messages that cross nodes implementing `JsonSerializable`
- Seed-node cluster formation on localhost

## Run it

The repository root has scripts that start several nodes of an example in the background:

```bash
# from the repository root
sh cluster-start.sh cluster io.github.seonwkim.example.SpringPekkoApplication 8080 2551 3
```

This starts three nodes: HTTP ports 8080, 8081, 8082 and remoting ports 2551, 2552, 2553. Each node logs to
`log_cluster_<port>.txt`. Give the cluster 10 to 20 seconds to form. Stop all three with:

```bash
sh cluster-stop.sh
```

To start the nodes by hand, use one terminal per node:

```bash
./gradlew :example:cluster:bootRun
./gradlew :example:cluster:bootRun --args='--server.port=8081 --spring.actor.remote.port=2552'
./gradlew :example:cluster:bootRun --args='--server.port=8082 --spring.actor.remote.port=2553'
```

### Sharded actors

```bash
curl "http://localhost:8080/hello?message=Hello&entityId=entity-1"
curl "http://localhost:8081/hello?message=World&entityId=entity-2"
# Received from entity [entity-2] on node [pekko://spring-pekko-example@127.0.0.1:2553]

# The same entity id answers from the same node, whichever node you call
curl "http://localhost:8080/hello?message=First&entityId=shared"
curl "http://localhost:8082/hello?message=Second&entityId=shared"
```

### Cluster singleton

```bash
# Record a metric from two different nodes
curl -X POST "http://localhost:8080/api/singleton/metrics?name=requests&value=100"
curl -X POST "http://localhost:8081/api/singleton/metrics?name=requests&value=200"

# Read everything the singleton holds, from any node
curl http://localhost:8082/api/singleton/metrics
# {"metrics":{...per node...},"singletonNodeAddress":"pekko://spring-pekko-example@127.0.0.1:2551"}

curl -X DELETE http://localhost:8080/api/singleton/metrics
```

## Configuration

```yaml
spring:
  actor:
    name: spring-pekko-example
    provider: cluster
    remote:
      hostname: 127.0.0.1
      port: 2551
    cluster:
      seed-nodes:
        - pekko://spring-pekko-example@127.0.0.1:2551
        - pekko://spring-pekko-example@127.0.0.1:2552
        - pekko://spring-pekko-example@127.0.0.1:2553
      downing:
        strategy: keep-majority
    pekko:
      actor:
        allow-java-serialization: off
```

Every node uses the same file; the start script overrides `server.port` and `spring.actor.remote.port`.
The seed-node addresses use the actor system name (`spring.actor.name`), so all nodes must share it.

## Walkthrough

### A sharded actor: `HelloActor`

A sharded actor declares an entity type. The cluster runs at most one instance per entity id and routes
every message for that id to it.

```java
@Component
public class HelloActor implements SpringShardedActor<HelloActor.Command> {

    public static final ShardedEntityType<Command> TYPE_KEY = ShardedEntityType.of(Command.class, "HelloActor");

    public interface Command extends JsonSerializable {}

    public static class SayHello extends AskCommand<String> implements Command {
        public final String message;

        public SayHello(String message) {
            this.message = message;
        }
    }

    @Override
    public ShardedEntityType<Command> entityType() {
        return TYPE_KEY;
    }

    @Override
    public SpringActorBehavior<Command> create(SpringShardedActorContext<Command> ctx) {
        final String entityId = ctx.getEntityId();
        return SpringActorBehavior.builder(Command.class, ctx)
                .withState(actorCtx -> new HelloActorBehavior(actorCtx, entityId))
                .onMessage(SayHello.class, HelloActorBehavior::onSayHello)
                .build();
    }
}
```

The handler replies with the entity id and `ctx.nodeAddress()`, so you can see where the entity runs.

`HelloService` gets a handle by id and asks. You never spawn a sharded actor yourself; the first message
starts it. The Kotlin tab shows the same call with `actor-kotlin`, from a `suspend` function.

=== "Java"

    ```java
    public Mono<String> hello(String message, String entityId) {
        SpringShardedActorHandle<HelloActor.Command> actorRef =
                springActorSystem.sharded(HelloActor.class).withId(entityId).get();

        CompletionStage<String> response = actorRef.ask(new HelloActor.SayHello(message))
                .withTimeout(Duration.ofSeconds(3))
                .onTimeout(() -> "Request timed out for entity: " + entityId)
                .execute();

        return Mono.fromCompletionStage(response);
    }
    ```

=== "Kotlin"

    ```kotlin
    suspend fun hello(message: String, entityId: String): String =
        actorSystem.sharded<HelloActor, _>()
            .withId(entityId)
            .get()
            .ask(HelloActor.SayHello(message))
            .withTimeout(3.seconds)
            .onTimeout { "Request timed out for entity: $entityId" }
            .await()
    ```

### A cluster singleton: `ClusterMetricsAggregator`

`ClusterMetricsAggregator` is a plain actor that keeps a map of metrics per node.
`ClusterSingletonService` spawns it as a cluster singleton when the app starts:

=== "Java"

    ```java
    // in a @PostConstruct method
    metricsAggregator = springActorSystem
            .actor(ClusterMetricsAggregator.class)
            .withId("metrics-aggregator")
            .asClusterSingleton()
            .spawn()
            .toCompletableFuture()
            .get();
    ```

=== "Kotlin"

    ```kotlin
    // spawnAndWait() blocks until the singleton proxy is ready, like spawn().get() in the Java tab
    val metricsAggregator = actorSystem.actor<ClusterMetricsAggregator, _>()
        .withId("metrics-aggregator")
        .asClusterSingleton()
        .spawnAndWait()
    ```

Every node runs this code, and every node gets a handle. The actor itself runs on one node only (the
oldest member); the handles on the other nodes forward to it. Recording a metric is a `tell`, reading
is an `ask` with a fallback:

```java
metricsAggregator.tell(new ClusterMetricsAggregator.RecordMetric(nodeAddress, metricName, value, timestamp));

metricsAggregator.ask(new ClusterMetricsAggregator.GetMetrics())
        .withTimeout(Duration.ofSeconds(3))
        .onTimeout(() -> new ClusterMetricsAggregator.MetricsResponse(Map.of(), "timeout"))
        .execute();
```

The response includes `singletonNodeAddress`, the node the singleton runs on.

## Try next

- **Singleton failover.** Find the singleton's node in `singletonNodeAddress`, stop that node (for
  port 8080: `kill $(lsof -t -i:8080)`), wait 10 to 20 seconds and read the metrics from another node.
  The singleton now runs elsewhere, and its map is empty: its state lived in memory.
- **Entity rebalancing.** Call several entity ids, stop one node, and call them again. Entities from the
  stopped node start again on the remaining nodes, with fresh state.
- Add a field to `SayHello` and keep it readable by both old and new nodes (see
  [Deployments](../guides/operations/deployments.md)).

## Troubleshooting

- **Cluster does not form:** wait 10 to 20 seconds; check that the seed nodes match and that every node
  uses the same `spring.actor.name`.
- **Asks time out across nodes:** check that the command classes implement `JsonSerializable` and that
  Jackson can write and read them.

## Related

- [Sharded actors](../guides/cluster/sharded-actors.md)
- [Cluster singletons](../guides/cluster/singletons.md)
- [Cluster formation](../guides/cluster/cluster-formation.md)
- [Split brain and leases](../guides/cluster/split-brain-and-leases.md)
- [Chat example](chat.md): sharded actors with pub/sub topics and WebSockets

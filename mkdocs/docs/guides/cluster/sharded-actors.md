# Sharded Actors

A sharded actor is an actor type whose instances, called **entities**, are spread over the nodes of a
cluster. Each entity has an id. The cluster runs at most one entity per id at a time, starts it on its
first message, and moves it when nodes join or leave. You send a message to an id from any node, and it
reaches the entity wherever it runs.

Use sharding when you have many things that each need their own actor: users, orders, devices, chat
rooms. It spreads them over the nodes, so you scale out by adding nodes.

For one actor for the whole cluster, use a [cluster singleton](singletons.md) instead. For actors that
live on one node only, use regular actors ([Actors](../core/actors.md)).

## Setup

Sharded actors need the cluster starter:

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

Then run the actor system in cluster mode. This is the configuration of the
[cluster example](../../examples/cluster.md), three nodes on one machine:

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
```

Each node uses its own `remote.port` (2551, 2552, 2553) and the same `spring.actor.name`. See
[Cluster Formation](cluster-formation.md) for joining without fixed addresses, and
[Split Brain and Leases](split-brain-and-leases.md) for the downing strategy.

## Write a sharded actor

Implement `SpringShardedActor` and annotate the class with `@Component`. The starter registers every
sharded actor bean with the cluster when the actor system starts.

=== "Java"

    ```java
    @Component
    public class HelloActor implements SpringShardedActor<HelloActor.Command> {

        public static final ShardedEntityType<Command> TYPE_KEY =
                ShardedEntityType.of(Command.class, "HelloActor");

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
            String entityId = ctx.getEntityId();
            return SpringActorBehavior.builder(Command.class, ctx)
                    .onMessage(SayHello.class, (behaviorCtx, msg) -> {
                        String reply = "Received from entity [" + entityId + "] on node ["
                                + behaviorCtx.nodeAddress() + "]";
                        behaviorCtx.getLog().info(reply);
                        msg.reply(reply);
                        return SpringActorBehavior.same();
                    })
                    .build();
        }
    }
    ```

=== "Kotlin"

    ```kotlin
    @Component
    class HelloActor : SpringShardedActor<HelloActor.Command> {

        companion object {
            val TYPE_KEY: ShardedEntityType<Command> = ShardedEntityType.of(Command::class.java, "HelloActor")
        }

        sealed interface Command : JsonSerializable

        class SayHello(val message: String) : AskCommand<String>(), Command

        override fun entityType(): ShardedEntityType<Command> = TYPE_KEY

        override fun create(ctx: SpringShardedActorContext<Command>): SpringActorBehavior<Command> {
            val entityId = ctx.entityId
            return actorBehavior<Command>(ctx)
                .onMessage(SayHello::class.java) { behaviorCtx, msg ->
                    val reply = "Received from entity [$entityId] on node [${behaviorCtx.nodeAddress()}]"
                    behaviorCtx.log.info(reply)
                    msg.reply(reply)
                    SpringActorBehavior.same()
                }
                .build()
        }
    }
    ```

What differs from a regular actor:

| Part | What it does |
|---|---|
| `ShardedEntityType.of(Command.class, "HelloActor")` | Names the entity type and its message type. Nodes reach the same entities when they use the same name, so keep it stable across deployments. |
| `entityType()` | Returns that type. |
| `create(SpringShardedActorContext<T>)` | Builds the behavior of one entity, on the node that hosts it. `ctx.getEntityId()` is the entity's id (also `ctx.actorId()`), and the same `SpringActorBehavior` builder works as for a regular actor, `withState` included. |
| `sharding()` | Optional: how ids map to shards. Default `ShardingStrategy.defaults()`, 100 shards. |
| `role()` | Optional: the cluster role whose nodes host the entities. Default: every node. |

Messages cross the network, so the command interface extends `JsonSerializable` (or `CborSerializable`),
and so do the replies when they are not `String` or a number.

## Send messages

`actorSystem.sharded(HelloActor.class).withId(id).get()` returns a handle to the entity with that id. Getting
a handle does not start the entity and costs nearly nothing: the entity starts on its first message.

=== "Java"

    ```java
    @Service
    public class HelloService {

        private final SpringActorSystem actorSystem;

        public HelloService(SpringActorSystem actorSystem) {
            this.actorSystem = actorSystem;
        }

        public Mono<String> hello(String message, String entityId) {
            SpringShardedActorHandle<HelloActor.Command> entity =
                    actorSystem.sharded(HelloActor.class).withId(entityId).get();

            CompletionStage<String> reply = entity
                    .ask(new HelloActor.SayHello(message))
                    .withTimeout(Duration.ofSeconds(3))
                    .onTimeout(() -> "Request timed out for entity: " + entityId)
                    .execute();

            return Mono.fromCompletionStage(reply);
        }
    }
    ```

=== "Kotlin"

    ```kotlin
    @Service
    class HelloService(private val actorSystem: SpringActorSystem) {

        suspend fun hello(message: String, entityId: String): String {
            val entity = actorSystem.sharded<HelloActor, _>().withId(entityId).get()
            return entity.ask(HelloActor.SayHello(message))
                .withTimeout(3.seconds)
                .onTimeout { "Request timed out for entity: $entityId" }
                .await()
        }
    }
    ```

The handle has `tell`, `ask`, `entityId()` and `entityType()`. `withTimeout` on the builder sets the default
ask timeout of the handle (3 seconds otherwise).

There is no `spawn`, `exists` or `stop` for an entity, and no need to cache handles: get one per request.

| | Regular actor | Sharded entity |
|---|---|---|
| Created | By you, with `spawn()` or `getOrSpawn()` | By the cluster, on its first message |
| Where it runs | The node that spawned it | One node of the cluster, chosen by its shard |
| How many per id | One per node | One per cluster |
| Handle | From `spawn`, `get` or `getOrSpawn` | From `sharded(...).withId(id).get()`, any time |
| Messages | Any Java object | Serializable (`JsonSerializable`, `CborSerializable`) |

## Shards and ids

Entities are grouped into shards: a hash of the entity id picks the shard, and the cluster places whole
shards on nodes. When a node joins or leaves, shards move, and their entities start again on their new node
on the next message.

=== "Java"

    ```java
    @Override
    public ShardingStrategy sharding() {
        return ShardingStrategy.hashed(300);
    }
    ```

=== "Kotlin"

    ```kotlin
    override fun sharding(): ShardingStrategy = ShardingStrategy.hashed(300)
    ```

- Use about ten times the largest number of nodes you expect. The default is 100.
- Every node must use the same strategy for an entity type, and it must stay the same across deployments:
  a different number of shards sends the same id to a different shard.
- Pick ids with many distinct values, such as user ids or order numbers, so the entities spread evenly.

## Run entities on some nodes only

Override `role()` and give the hosting nodes that role:

=== "Java"

    ```java
    @Override
    public Optional<String> role() {
        return Optional.of("backend");
    }
    ```

=== "Kotlin"

    ```kotlin
    override fun role(): Optional<String> = Optional.of("backend")
    ```

```yaml
spring:
  actor:
    cluster:
      roles: [backend]
```

Nodes without the role still send messages to the entities, through a proxy; they just don't host any.

## State and passivation

An entity's state lives in memory. It is lost when the entity stops: when its shard moves to another node,
when its node stops, or when the entity is passivated.

Passivation is Pekko's: an entity that receives no message for a while is stopped to free memory, and
started again on its next message. Pekko's default idle timeout is 2 minutes. Change it with the raw key:

```yaml
spring:
  actor:
    pekko:
      cluster:
        sharding:
          passivation:
            default-idle-strategy:
              idle-entity:
                timeout: 10m
```

When the state must survive, load it when the entity starts and save it when it changes, as in
[State in a Database](../data/state-in-a-database.md), or use an
[event-sourced actor](../data/event-sourcing.md).

## One entity per id, even in a split

In a network split, both sides may start the same shard. A downing strategy stops one side; with
`spring.actor.cluster.sharding.use-lease: true`, each shard also takes a lease before it starts its
entities, so a shard never runs on two nodes. See [Split Brain and Leases](split-brain-and-leases.md#leases).

## Monitoring

With `actor-metrics` and Micrometer, each node reports `actor.sharding.regions`, `actor.sharding.shards` and
`actor.sharding.entities`, tagged `type` with the entity type name. The `actors` Actuator endpoint lists,
for each entity type, the regions and the shards and entities on this node. See
[Monitoring](../operations/monitoring.md).

## Pitfalls

- **Unstable names or shard counts.** Renaming the entity type or changing the number of shards sends
  existing ids to other entities. Keep both fixed.
- **Blocking in an entity.** Entities run on the default dispatcher. Use a non-blocking client, or hand
  the blocking call off and send the result back with `pipeToSelf` (see
  [State in a Database](../data/state-in-a-database.md#non-blocking-calls-with-r2dbc)).
- **Many messages between entities.** Every message to another entity may cross the network. Keep what is
  used together in one entity.
- **Holding state only in memory.** It is gone after passivation or a move. Store what must survive.

## Related

- [Cluster example](../../examples/cluster.md): sharded actors on three nodes
- [Cluster Singletons](singletons.md): one actor for the whole cluster
- [Split Brain and Leases](split-brain-and-leases.md): downing and shard leases
- [State in a Database](../data/state-in-a-database.md) and [Event-Sourced Actors](../data/event-sourcing.md):
  keep an entity's state
- [Testing](../testing.md#cluster-tests-with-actorcluster): several nodes in one test

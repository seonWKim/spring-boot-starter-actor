# Sharded Actors

This guide explains how to create and use sharded actors in a clustered environment using Spring Boot Starter Actor.

## What are Sharded Actors?

Sharded actors are actors that are distributed across multiple nodes in a cluster. Each actor instance (entity) is responsible for a specific entity ID, and the cluster ensures that only one instance of an entity exists across the entire cluster at any given time.

**Sharding is useful when:**

- You need to distribute actor instances across multiple nodes
- You have a large number of actors that would be too much for a single node
- You want automatic rebalancing of actors when nodes join or leave the cluster

!!! info "Cluster Sharding"
    Cluster sharding provides location transparency - you can send messages to entities without knowing which node they're on.

## Setting Up a Cluster

Before you can use sharded actors, you need to set up a Pekko cluster. Add the following configuration to your `application.yml` file:

```yaml 
spring:
  application:
    name: spring-pekko
  actor:
    pekko:
      name: spring-pekko-example
      actor:
        provider: cluster
        allow-java-serialization: off
        warn-about-java-serializer-usage: on
      remote:
        artery:
          canonical:
            hostname: 127.0.0.1
            port: 2551
      cluster:
        name: spring-pekko-example
        seed-nodes:
          - pekko://spring-pekko-example@127.0.0.1:2551
          - pekko://spring-pekko-example@127.0.0.1:2552
          - pekko://spring-pekko-example@127.0.0.1:2553
        downing-provider-class: org.apache.pekko.cluster.sbr.SplitBrainResolverProvider

server:
  port: 8080
```

!!! note "Configuration Notes"
    - Adjust the hostname, port, and seed-nodes according to your environment
    - For production, use proper hostnames and DNS instead of localhost
    - The downing provider handles split-brain scenarios

## Creating a Sharded Actor

To create a sharded actor, implement the `SpringShardedActor` interface and annotate the class with `@Component`:

```java
@Component
public class HelloActor implements SpringShardedActor<HelloActor.Command> {

    public static final EntityTypeKey<Command> TYPE_KEY =
            EntityTypeKey.create(Command.class, "HelloActor");

    // Define the command interface for messages this actor can handle
    public interface Command extends JsonSerializable {}

    // Define a message type
    public static class SayHello extends AskCommand<String> implements Command {
        public final String message;

        public SayHello(String message) {
            this.message = message;
        }
    }

    @Override
    public EntityTypeKey<Command> typeKey() {
        return TYPE_KEY;
    }

    @Override
    public SpringShardedActorBehavior<Command> create(SpringShardedActorContext<Command> ctx) {
        final String entityId = ctx.getEntityId();

        return SpringShardedActorBehavior.builder(Command.class, ctx)
                .withState(actorCtx -> new HelloActorBehavior(actorCtx, entityId))
                .onMessage(SayHello.class, HelloActorBehavior::onSayHello)
                .build();
    }

    /**
     * Behavior handler for hello actor. Holds the entity ID and handles messages.
     */
    private static class HelloActorBehavior {
        private final ActorContext<Command> ctx;
        private final String entityId;

        HelloActorBehavior(ActorContext<Command> ctx, String entityId) {
            this.ctx = ctx;
            this.entityId = entityId;
        }

        /**
         * Handles SayHello commands by responding with node and entity information.
         *
         * @param msg The SayHello message
         * @return The next behavior (same in this case)
         */
        private Behavior<Command> onSayHello(SayHello msg) {
            // Get information about the current node and entity
            final String nodeAddress = ctx.getSystem().address().toString();

            // Create a response message with node and entity information
            final String message = "Received from entity [" + entityId + "] on node [" + nodeAddress + "]";

            // Send the response back to the caller
            msg.reply(message);

            // Log the message for debugging
            ctx.getLog().info(message);

            return Behaviors.same();
        }
    }

    @Override
    public ShardingMessageExtractor<ShardEnvelope<Command>, Command> extractor() {
        return new DefaultShardingMessageExtractor<>(3);
    }
}
```

**Key differences from a regular actor:**

1. Implement `SpringShardedActor<T>` instead of `SpringActor<T>`
2. Commands must implement `JsonSerializable` (or `CborSerializable`) for serialization across the network
3. Define an `EntityTypeKey` for the actor type
4. Override `typeKey()` to return the EntityTypeKey
5. Override `create(SpringShardedActorContext<T>)` to return `SpringShardedActorBehavior<T>` instead of `SpringActorBehavior<T>`
6. Use `SpringShardedActorBehavior.builder()` instead of `SpringActorBehavior.builder()`
7. Override `extractor()` to provide a sharding message extractor

!!! tip "Serialization"
    Always use `JsonSerializable` or `CborSerializable` for sharded actor messages to ensure they can be sent across the cluster.

## Interacting with Sharded Actors

To interact with sharded actors, you use the `sharded` method of the `SpringActorSystem`:

```java
@Service
public class HelloService {

    private final SpringActorSystem springActorSystem;

    public HelloService(SpringActorSystem springActorSystem) {
        this.springActorSystem = springActorSystem;
    }

    /**
     * Best practice for sharded actors:
     * - Get reference on each request (references are lightweight)
     * - No need to cache (entities are managed by cluster sharding)
     * - No need to check existence (entities are created on-demand)
     * - Use ask for timeout and error handling
     */
    public Mono<String> hello(String message, String entityId) {
        // Get a reference to the sharded actor entity
        SpringShardedActorRef<HelloActor.Command> actorRef =
                springActorSystem.sharded(HelloActor.class).withId(entityId).get();

        // Send the message using the fluent ask builder with timeout and error handling
        CompletionStage<String> response = actorRef
                .ask(new HelloActor.SayHello(message))
                .withTimeout(Duration.ofSeconds(3))
                .onTimeout(() -> "Request timed out for entity: " + entityId)
                .execute();

        // Convert the CompletionStage to a Mono for reactive programming
        return Mono.fromCompletionStage(response);
    }
}
```

The `sharded` method creates a builder that:

1. Takes the actor class as a parameter
2. Requires setting the entity ID using `withId()`
3. Returns the actor reference with `get()`

The builder pattern provides a more fluent API and automatically resolves the `EntityTypeKey` from the actor class.

### Key Differences from Regular Actors

**Sharded actors behave differently from regular actors:**

1. **No `start()` needed**: Entities are created automatically when the first message arrives
2. **Always available**: Entity references are always valid, even if the entity isn't currently running
3. **Auto-distribution**: Entities are automatically distributed across cluster nodes
4. **Auto-passivation**: Entities are automatically stopped after idle timeout (configurable)
5. **Lightweight references**: Getting a reference doesn't create the entity, so no caching needed
6. **No exists/get checks**: The cluster sharding coordinator manages entity lifecycle

**Example - Regular Actor vs Sharded Actor:**

```java
// Regular Actor - needs explicit lifecycle management
CompletionStage<SpringActorRef<Command>> actor = actorSystem
    .exists(MyActor.class, "actor-1")
    .thenCompose(exists -> {
        if (exists) {
            return actorSystem.get(MyActor.class, "actor-1");
        } else {
            return actorSystem.actor(MyActor.class)
                .withId("actor-1")
                .spawn();
        }
    });

// Sharded Actor - just get the reference and use it
SpringShardedActorRef<Command> actor = actorSystem
    .sharded(MyShardedActor.class)
    .withId("actor-1")
    .get();
```

!!! success "Automatic Lifecycle"
    Sharded actors don't require explicit `start()` or lifecycle management. Simply get a reference and send messages - the entity is created automatically on first message.

## Using Sharded Actors in a REST Controller

Here's an example of how to use the sharded actor service in a REST controller:

```java
@RestController
public class HelloController {

    private final HelloService helloService;

    public HelloController(HelloService helloService) {
        this.helloService = helloService;
    }

    @GetMapping("/hello")
    public Mono<String> hello(@RequestParam String message, @RequestParam String entityId) {
        return helloService.hello(message, entityId);
    }
}
```

## Entity ID Strategies

The entity ID is a crucial part of sharding. It determines which shard an entity belongs to, and therefore which node in the cluster will host the entity.

**Common strategies for choosing entity IDs:**

1. **Natural Keys** - Use existing business identifiers (e.g., user IDs, order numbers)
2. **Composite Keys** - Combine multiple fields to form a unique identifier
3. **Hash-Based Keys** - Generate a hash from the entity's data
4. **UUID** - Generate a random UUID for each entity

!!! tip "ID Selection"
    Choose a strategy that ensures even distribution of entities across shards while maintaining the ability to locate entities when needed.

## Sharding Configuration

You can configure sharding behavior using the `extractor()` method in your sharded actor:

```java

@Override
public ShardingMessageExtractor<ShardEnvelope<Command>, Command> extractor() {
    return new DefaultShardingMessageExtractor<>(numberOfShards);
}
```

The `DefaultShardingMessageExtractor` takes a parameter that specifies the number of shards to use. More shards
allow for finer-grained distribution but increase overhead.

## Best Practices for Sharded Actors

1. **Don't Cache References** - Get references on each request; they're lightweight and don't create entities
2. **Use ask** - Always use `ask()` with timeout and error handling for production code
3. **Design for Idempotency** - Messages may be redelivered during rebalancing, so design handlers to be idempotent
4. **Choose Entity IDs Wisely** - Use natural business keys for even distribution across shards
5. **Avoid Cross-Entity Dependencies** - Minimize communication between entities to reduce network overhead
6. **Monitor Shard Distribution** - Use built-in metrics to ensure entities are evenly distributed
7. **Configure Passivation** - Tune idle timeout based on your use case to balance memory and startup costs
8. **Use JSON Serialization** - Prefer `JsonSerializable` over Java serialization for better performance and compatibility

!!! warning "Cross-Entity Communication"
    Minimize dependencies between sharded entities. Each cross-entity message incurs network overhead and can impact performance.

## Comparison: Regular vs Sharded Actors

| Feature | Regular Actor | Sharded Actor |
|---------|--------------|---------------|
| Lifecycle | Manual (spawn/start/stop) | Automatic (on-demand) |
| Distribution | Single node | Distributed across cluster |
| Reference Creation | Heavy (creates actor) | Lightweight (just reference) |
| Caching | Required for performance | Not needed |
| Existence Check | Use `exists()` | Not needed |
| Serialization | Not required | Required (`JsonSerializable`) |
| Use Case | Local, long-lived actors | Distributed, large-scale entities |

## Next Steps

Now that you know how to create and use sharded actors, you can:

1. [Explore the Cluster Example](../examples/cluster.md) - See sharded actors in action
2. [Learn about Persistence](persistence-spring-boot.md) - Add state persistence to your actors
3. [Set up Monitoring](../examples/monitoring.md) - Track cluster health and performance

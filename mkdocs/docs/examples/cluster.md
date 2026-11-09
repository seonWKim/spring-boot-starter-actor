# Cluster Example

This guide demonstrates how to use Actor Spring Boot Starter in a clustered environment, focusing on sharded actors.

## Overview

The cluster example shows how to:

- Create and use sharded actors across a cluster
- Distribute actor instances across multiple nodes
- Send messages to specific entity instances
- Handle entity state in a distributed environment

This example demonstrates the power of the actor model for building scalable, distributed applications with Spring Boot.

!!! info "Cluster Sharding"
    Cluster sharding automatically distributes entities across nodes and provides location transparency for messaging.

## Source Code

You can find the complete source code for this example on GitHub:

[Cluster Example Source Code](https://github.com/seonWKim/actor-spring-boot-starter/tree/main/example/cluster)

!!! tip "Prerequisites"
    Familiarity with the [Simple Example](simple.md) is recommended before diving into cluster concepts.

## Key Components

### HelloActor

`HelloActor` is a sharded actor that responds to messages in a clustered environment. Each entity is a separate instance identified by an entity ID. The actor demonstrates:

- How to implement the `SpringShardedActor` interface
- How to define serializable message types for cluster communication
- How to create entity behaviors
- How to handle messages in a clustered environment

```java
@Component
public class HelloActor implements SpringShardedActor<HelloActor.Command> {

    public static final ShardedEntityType<Command> TYPE_KEY = ShardedEntityType.of(Command.class, "HelloActor");

    /** Base interface for all commands that can be sent to the hello actor. */
    public interface Command extends JsonSerializable {}

    /** Command to say hello and get a response. */
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

    /**
     * Creates the behavior for this actor when it's started.
     *
     * @param ctx The sharded actor context containing information about this entity
     * @return The behavior for the actor
     */
    @Override
    public SpringActorBehavior<Command> create(SpringShardedActorContext<Command> ctx) {
        final String entityId = ctx.getEntityId();

        return SpringActorBehavior.builder(Command.class, ctx)
                .withState(actorCtx -> new HelloActorBehavior(actorCtx, entityId))
                .onMessage(SayHello.class, HelloActorBehavior::onSayHello)
                .build();
    }

    /**
     * Behavior handler for hello actor. Holds the entity ID and handles messages.
     */
    private static class HelloActorBehavior {
        private final SpringBehaviorContext<Command> ctx;
        private final String entityId;

        HelloActorBehavior(SpringBehaviorContext<Command> ctx, String entityId) {
            this.ctx = ctx;
            this.entityId = entityId;
        }

        /**
         * Handles SayHello commands by responding with node and entity information.
         *
         * @param msg The SayHello message
         * @return The next behavior (same in this case)
         */
        private SpringActorBehavior<Command> onSayHello(SayHello msg) {
            // Get information about the current node and entity
            final String nodeAddress = ctx.nodeAddress();

            // Create a response message with node and entity information
            final String message = "Received from entity [" + entityId + "] on node [" + nodeAddress + "]";

            // Send the response back to the caller
            msg.reply(message);

            // Log the message for debugging
            ctx.getLog().info(message);

            return SpringActorBehavior.same();
        }
    }
}
```

### HelloService

`HelloService` acts as an intermediary between the REST API and the actor system:

- It gets references to actor entities using the sharded method
- It provides methods to send messages to specific entities and return the responses
- It converts actor responses to reactive Mono objects for use with Spring WebFlux

```java
@Service
public class HelloService {

    private final SpringActorSystem springActorSystem;

    /**
     * Creates a new HelloService with the given actor system.
     *
     * @param springActorSystem The Spring actor system
     */
    public HelloService(SpringActorSystem springActorSystem) {
        this.springActorSystem = springActorSystem;
    }

    /**
     * Sends a hello message to a sharded actor entity.
     *
     * <p>Best practices for sharded actors:
     * <ul>
     *   <li>Get reference on each request (references are lightweight)
     *   <li>No need to cache (entities are managed by cluster sharding)
     *   <li>No need to check existence (entities are created on-demand)
     *   <li>Use ask() for timeout and error handling
     * </ul>
     *
     * @param message The message to send
     * @param entityId The ID of the entity to send the message to
     * @return A Mono containing the response from the actor
     */
    public Mono<String> hello(String message, String entityId) {
        // Get a reference to the actor entity
        SpringShardedActorHandle<HelloActor.Command> actorRef =
                springActorSystem.sharded(HelloActor.class).withId(entityId).get();

        // Send the message using the fluent ask API with timeout and error handling
        CompletionStage<String> response = actorRef.ask(new HelloActor.SayHello(message))
                .withTimeout(Duration.ofSeconds(3))
                .onTimeout(() -> "Request timed out for entity: " + entityId)
                .execute();

        // Convert the CompletionStage to a Mono for reactive programming
        return Mono.fromCompletionStage(response);
    }
}
```

### HelloController

`HelloController` exposes the actor functionality via a REST API:

- It injects the HelloService
- It defines REST endpoints that call the service methods with entity IDs
- It returns the actor responses as HTTP responses

```java
@RestController
public class HelloController {

    private final HelloService helloService;

    /**
     * Creates a new HelloController with the given service.
     *
     * @param helloService The service for interacting with HelloActors
     */
    public HelloController(HelloService helloService) {
        this.helloService = helloService;
    }

    /**
     * Endpoint to send a hello message to an actor entity.
     *
     * @param message The message to send
     * @param entityId The ID of the entity to send the message to
     * @return A Mono containing the response from the actor
     */
    @GetMapping("/hello")
    public Mono<String> hello(@RequestParam String message, @RequestParam String entityId) {
        return helloService.hello(message, entityId);
    }
}
```

## Configuration

The cluster example requires additional configuration to set up the actor cluster:

```yaml
spring:
  application:
    name: spring-pekko
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
    pekko:
      actor:
        allow-java-serialization: off
        warn-about-java-serializer-usage: on
      cluster:
        name: spring-pekko-example
        downing-provider-class: org.apache.pekko.cluster.sbr.SplitBrainResolverProvider

server:
  port: 8080
```

!!! warning "Serialization"
    Java serialization is disabled for security and performance. Always use `JsonSerializable` or `CborSerializable` for cluster messages.

## Running the Example

To run the cluster example:

1. **Start multiple instances** with different ports:
```bash
sh cluster-start.sh cluster io.github.seonwkim.example.SpringPekkoApplication 8080 2551 3
```

2. **Send messages** to test entity distribution:
```bash
# Try different entity IDs to see distribution
curl "http://localhost:8080/hello?message=test&entityId=entity-1"
curl "http://localhost:8081/hello?message=test&entityId=entity-2"
curl "http://localhost:8082/hello?message=test&entityId=entity-3"
```

3. **Observe behavior:**
- The same entity ID always routes to the same node
- Different entity IDs are distributed across the cluster
- Try sending the same entity ID to different nodes to verify routing

!!! tip "Entity Affinity"
    Notice that regardless of which node receives the HTTP request, messages for the same entity ID always go to the same actor instance in the cluster.

## Entity Benefits

Entities in Actor Spring Boot Starter provide:

- **Automatic distribution** across the cluster
- **Location transparency** for messaging - send messages without knowing which node hosts the entity
- **Scalability** with cluster expansion - add nodes to increase capacity
- **Fault tolerance** with automatic recreation - entities restart on different nodes if a node fails
- **Simplified state management** - each entity maintains its own isolated state

!!! success "Scalability"
    Simply add more nodes to the cluster to increase capacity. The system automatically rebalances entities across all nodes.

## Next Steps

- [Sharded Actors Guide](../guides/sharded-actors.md) - Deep dive into cluster sharding concepts
- [Chat Example](chat.md) - Build a distributed real-time chat application
- [Synchronization Example](synchronization.md) - Compare actor-based vs traditional synchronization

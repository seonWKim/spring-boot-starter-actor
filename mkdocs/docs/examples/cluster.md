# Cluster Example

This guide demonstrates how to use Spring Boot Starter Actor in a clustered environment, focusing on how entities can be easily used with the library.

## Overview

The cluster example shows how to:

- Create and use sharded actors across a cluster
- Distribute actor instances across multiple nodes
- Send messages to specific entity instances
- Handle entity state in a distributed environment

This example demonstrates the power of the actor model for building scalable, distributed applications with Spring Boot.

## Source Code

You can find the complete source code for this example on GitHub:
[https://github.com/seonWKim/spring-boot-starter-actor/tree/main/example/cluster](https://github.com/seonWKim/spring-boot-starter-actor/tree/main/example/cluster)

## Key Components

### HelloActor

`HelloActor` is a sharded actor that responds to messages in a clustered environment. Each entity is a separate instance identified by an entity ID. The actor demonstrates:

- How to implement the `ShardedActor` interface
- How to define serializable message types for cluster communication
- How to create entity behaviors
- How to handle messages in a clustered environment

```java
@Component
public class HelloActor implements ShardedActor<HelloActor.Command> {
    public static final EntityTypeKey<Command> TYPE_KEY =
            EntityTypeKey.create(Command.class, "HelloActor");

    // Command interface and message types
    public interface Command extends JsonSerializable {}

    public static class SayHello implements Command {
        public final ActorRef<String> replyTo;
        public final String message;

        @JsonCreator
        public SayHello(
                @JsonProperty("replyTo") ActorRef<String> replyTo,
                @JsonProperty("message") String message) {
            this.replyTo = replyTo;
            this.message = message;
        }
    }

    @Override
    public EntityTypeKey<Command> typeKey() {
        return TYPE_KEY;
    }

    @Override
    public ShardedActorBehavior<Command> create(EntityContext<Command> ctx) {
        final String entityId = ctx.getEntityId();

        return ShardedActorBehavior.builder(Command.class, ctx)
                .onCreate(actorCtx -> new HelloActorBehavior(actorCtx, entityId))
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
         */
        private Behavior<Command> onSayHello(SayHello msg) {
            // Get information about the current node and entity
            final String nodeAddress = ctx.getSystem().address().toString();

            // Create a response message with node and entity information
            final String message = "Received from entity [" + entityId + "] on node [" + nodeAddress + "]";

            // Send the response back to the caller
            msg.replyTo.tell(message);

            return Behaviors.same();
        }
    }

    @Override
    public ShardingMessageExtractor<ShardEnvelope<Command>, Command> extractor() {
        return new DefaultShardingMessageExtractor<>(3);
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

    public HelloService(SpringActorSystem springActorSystem) {
        this.springActorSystem = springActorSystem;
    }

    public Mono<String> hello(String message, String entityId) {
        // Get a reference to the actor entity
        SpringShardedActorRef<HelloActor.Command> actorRef =
                springActorSystem.sharded(HelloActor.class).withId(entityId).get();

        // Send the message to the actor and get the response
        CompletionStage<String> response = actorRef
                .askBuilder(replyTo -> new HelloActor.SayHello(replyTo, message))
                .withTimeout(Duration.ofSeconds(3))
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

    public HelloController(HelloService helloService) {
        this.helloService = helloService;
    }

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

## Running the Example

To run the cluster example:

1. Start multiple instances of the application with different ports
2. Access the `/hello` endpoint with a message and entity ID
3. Observe how the same entity ID always routes to the same node
4. Try different entity IDs to see how they are distributed across the cluster

## Entity Benefits

Entities in Spring Boot Starter Actor provide:

- Automatic distribution across the cluster
- Location transparency for messaging
- Scalability with cluster expansion
- Fault tolerance with automatic recreation
- Simplified state management

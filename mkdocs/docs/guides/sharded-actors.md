# Sharded Actors

This guide explains how to create and use sharded actors in a clustered environment using Spring Boot Starter
Actor.

## What are Sharded Actors?

Sharded actors are actors that are distributed across multiple nodes in a cluster. Each actor instance (entity)
is responsible for a specific entity ID, and the cluster ensures that only one instance of an entity exists
across the entire cluster at any given time.

Sharding is useful when:

- You need to distribute actor instances across multiple nodes
- You have a large number of actors that would be too much for a single node
- You want automatic rebalancing of actors when nodes join or leave the cluster

## Setting Up a Cluster

Before you can use sharded actors, you need to set up a Pekko cluster. Add the following configuration to your
`application.yaml` file:

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

Make sure to adjust the hostname, port, and seed-nodes according to your environment.

## Creating a Sharded Actor

To create a sharded actor, implement the `ShardedActor` interface and annotate the class with `@Component`:

```java
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.github.seonwkim.core.serialization.JsonSerializable;
import io.github.seonwkim.core.shard.DefaultShardingMessageExtractor;
import io.github.seonwkim.core.shard.ShardEnvelope;
import io.github.seonwkim.core.shard.ShardedActor;

import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.cluster.sharding.typed.ShardingMessageExtractor;
import org.apache.pekko.cluster.sharding.typed.javadsl.EntityContext;
import org.apache.pekko.cluster.sharding.typed.javadsl.EntityTypeKey;
import org.springframework.stereotype.Component;

@Component
public class HelloActor implements ShardedActor<HelloActor.Command> {

    public static final EntityTypeKey<Command> TYPE_KEY =
            EntityTypeKey.create(Command.class, "HelloActor");

    // Define the command interface for messages this actor can handle
    public interface Command extends JsonSerializable {}

    // Define a message type
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
    public Behavior<Command> create(EntityContext<Command> ctx) {
        return Behaviors.setup(
                context ->
                        Behaviors.receive(Command.class)
                                 .onMessage(
                                         SayHello.class,
                                         msg -> {
                                             // Get information about the current node and entity
                                             final String nodeAddress =
                                                     context.getSystem().address().toString();
                                             final String entityId = ctx.getEntityId();

                                             // Create a response message with node and entity information
                                             final String message =
                                                     "Received from entity [" + entityId + "] on node ["
                                                     + nodeAddress + "]";

                                             // Send the response back to the caller
                                             msg.replyTo.tell(message);

                                             // Log the message for debugging
                                             context.getLog().info(message);

                                             return Behaviors.same();
                                         })
                                 .build());
    }

    @Override
    public ShardingMessageExtractor<ShardEnvelope<Command>, Command> extractor() {
        return new DefaultShardingMessageExtractor<>(3);
    }
}
```

Key differences from a regular actor:

1. Implement `ShardedActor<T>` instead of `SpringActor`
2. Commands must implement `JsonSerializable`(or `CborSerializable`) for serialization across the network
3. Define an `EntityTypeKey` for the actor type
4. Override `typeKey()` to return the EntityTypeKey
5. Override `create(EntityContext<T>)` instead of `create(String)`
6. Override `extractor()` to provide a sharding message extractor
7. Use Jackson annotations (`@JsonCreator`, `@JsonProperty`) for message serialization

## Interacting with Sharded Actors

To interact with sharded actors, you use the `entityRef` method of the `SpringActorSystem`:

```java
import io.github.seonwkim.core.SpringActorSystem;
import io.github.seonwkim.core.SpringShardedActorRef;

import java.time.Duration;
import java.util.concurrent.CompletionStage;

import org.springframework.stereotype.Service;

import reactor.core.publisher.Mono;

@Service
public class HelloService {

    private final SpringActorSystem springActorSystem;

    public HelloService(SpringActorSystem springActorSystem) {
        this.springActorSystem = springActorSystem;
    }

    public Mono<String> hello(String message, String entityId) {
        // Get a reference to the actor entity
        SpringShardedActorRef<HelloActor.Command> actorRef =
                springActorSystem.entityRef(HelloActor.TYPE_KEY, entityId);

        // Send the message to the actor and get the response
        CompletionStage<String> response =
                actorRef.ask(replyTo -> new HelloActor.SayHello(replyTo, message), Duration.ofSeconds(3));

        // Convert the CompletionStage to a Mono for reactive programming
        return Mono.fromCompletionStage(response);
    }
}
```

The `entityRef` method takes two parameters:

1. The `EntityTypeKey` for the actor type
2. The entity ID that identifies the specific entity

The method returns a `SpringShardedActorRef<T>` that can be used to send messages to the entity.

## Using Sharded Actors in a REST Controller

Here's an example of how to use the sharded actor service in a REST controller:

```java
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import reactor.core.publisher.Mono;

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

The entity ID is a crucial part of sharding. It determines which shard an entity belongs to, and therefore which
node in the cluster will host the entity. Here are some strategies for choosing entity IDs:

1. **Natural Keys**: Use existing business identifiers (e.g., user IDs, order numbers)
2. **Composite Keys**: Combine multiple fields to form a unique identifier
3. **Hash-Based Keys**: Generate a hash from the entity's data
4. **UUID**: Generate a random UUID for each entity

Choose a strategy that ensures even distribution of entities across shards while maintaining the ability to
locate entities when needed.

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

## Next Steps

Now that you know how to create and use sharded actors, you can:

1. Explore the [API Reference](../api-reference.md) for detailed information about the library's APIs
2. Learn about advanced topics like persistence, event sourcing, and cluster singleton actors

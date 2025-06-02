# API Reference

This page provides a reference for the key interfaces and classes in Spring Boot Starter Actor.

## Core Interfaces

### SpringActor

The `SpringActor` interface is the base interface for all actors in Spring Boot Starter Actor. It defines the
contract for creating actors that can be managed by Spring.

```java
public interface SpringActor {
    /**
     * Returns the class of commands this actor can handle.
     *
     * @return The command class
     */
    Class<?> commandClass();

    /**
     * Creates the behavior for this actor when it's started.
     *
     * @param id The ID of the actor
     * @return The behavior for the actor
     */
    Behavior<?> create(String id);
}
```

### ShardedActor

The `ShardedActor` interface extends `SpringActor` and adds support for sharding in a clustered environment.

```java
public interface ShardedActor<T> {
    /**
     * Returns the entity type key for this actor.
     *
     * @return The entity type key
     */
    EntityTypeKey<T> typeKey();

    /**
     * Creates the behavior for this actor when it's started.
     *
     * @param ctx The entity context containing information about this entity
     * @return The behavior for the actor
     */
    Behavior<T> create(EntityContext<T> ctx);

    /**
     * Provides a message extractor for sharding. This determines how messages are routed to the
     * correct entity.
     *
     * @return The sharding message extractor
     */
    ShardingMessageExtractor<ShardEnvelope<T>, T> extractor();
}
```

## Core Classes

### SpringActorSystem

The `SpringActorSystem` class is the main entry point for interacting with actors. It provides methods for
spawning actors and getting references to sharded actors.

```java
public class SpringActorSystem {
    /**
     * Spawns an actor with the given spawn context.
     *
     * @param spawnContext The context containing all parameters needed to spawn the actor
     * @return A CompletionStage that resolves to a reference to the spawned actor
     */
    public <T> CompletionStage<SpringActorRef<T>> spawn(SpringActorSpawnContext<T> spawnContext);

    /**
     * Asynchronously stops a previously spawned actor with the given stop context.
     *
     * @param stopContext The context containing all parameters needed to stop the actor
     * @return A CompletionStage that completes when the stop command has been processed
     */
    public <T> CompletionStage<StopResult> stop(SpringActorStopContext<T> stopContext);

    /**
     * Gets a reference to a sharded actor entity.
     *
     * @param typeKey The entity type key
     * @param entityId The entity ID
     * @return A reference to the sharded actor entity
     */
    public <T> SpringShardedActorRef<T> entityRef(EntityTypeKey<T> typeKey, String entityId);
}
```

### SpringActorRef

The `SpringActorRef` class represents a reference to an actor. It provides methods for sending messages to the
actor.

```java
public class SpringActorRef<T> {
    /**
     * Sends a message to the actor and expects a response.
     *
     * @param messageFactory A function that creates the message, given a reply-to actor reference
     * @param timeout The timeout for the ask operation
     * @return A CompletionStage that resolves to the response from the actor
     */
    public <R> CompletionStage<R> ask(
            Function<ActorRef<R>, T> messageFactory, Duration timeout);

    /**
     * Sends a message to the actor without expecting a response.
     *
     * @param message The message to send
     */
    public void tell(T message);
}
```

### SpringShardedActorRef

The `SpringShardedActorRef` class represents a reference to a sharded actor entity. It provides methods for
sending messages to the entity.

```java
public class SpringShardedActorRef<T> {
    /**
     * Sends a message to the entity and expects a response.
     *
     * @param messageFactory A function that creates the message, given a reply-to actor reference
     * @param timeout The timeout for the ask operation
     * @return A CompletionStage that resolves to the response from the entity
     */
    public <R> CompletionStage<R> ask(
            Function<ActorRef<R>, T> messageFactory, Duration timeout);

    /**
     * Sends a message to the entity without expecting a response.
     *
     * @param message The message to send
     */
    public void tell(T message);
}
```

## Serialization

Refer to `JsonSerializable` or `CborSerializable`.

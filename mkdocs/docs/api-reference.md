# API Reference

This page provides a reference for the key interfaces and classes in Spring Boot Starter Actor.

## Core Interfaces

### SpringActor

The `SpringActor` interface is the base interface for all actors in Spring Boot Starter Actor. It defines the
contract for creating actors that can be managed by Spring.

```java
public interface SpringActor<A extends SpringActor<A, C>, C> {
    /**
     * Creates a behavior for this actor. This method is called by the actor system when a new actor
     * is created.
     *
     * @param actorContext The context of the actor
     * @return A behavior for the actor
     */
    Behavior<C> create(SpringActorContext actorContext);
}
```

### ShardedActor

The `ShardedActor` interface provides support for sharding actors across a cluster.

```java
public interface ShardedActor<T> {
    /**
     * Returns the entity type key for this actor type. The entity type key is used to identify the
     * actor type in the cluster.
     *
     * @return The entity type key for this actor type
     */
    EntityTypeKey<T> typeKey();

    /**
     * Creates a behavior for the actor given an entity context. This method is called when a new
     * instance of the actor is created.
     *
     * @param ctx The entity context for the actor
     * @return A behavior for the actor
     */
    Behavior<T> create(EntityContext<T> ctx);

    /**
     * Returns a message extractor for this actor type. The message extractor is used to extract
     * entity IDs and shard IDs from messages.
     *
     * @return A message extractor for this actor type
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
    public <A extends SpringActor<A, T>, T> CompletionStage<SpringActorRef<T>> spawn(SpringActorSpawnContext<A, T> spawnContext);

    /**
     * Asynchronously stops a previously spawned actor with the given stop context.
     *
     * @param stopContext The context containing all parameters needed to stop the actor
     * @return A CompletionStage that completes when the stop command has been processed
     */
    public <A extends SpringActor<A, C>, C> CompletionStage<StopResult> stop(SpringActorStopContext<A, C> stopContext);

    /**
     * Creates a fluent builder for getting a reference to a sharded actor.
     *
     * @param actorClass The class of the sharded actor
     * @return A builder for configuring and getting the sharded actor reference
     */
    public <T> SpringShardedActorBuilder<T> sharded(Class<? extends ShardedActor<T>> actorClass);
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

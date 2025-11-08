# API Reference

This page provides a reference for the key interfaces and classes in Spring Boot Starter Actor.

## Core Interfaces

### SpringActor

The `SpringActor` interface is the base interface for all actors in Spring Boot Starter Actor. It defines the
contract for creating actors that can be managed by Spring.

```java
public interface SpringActor<C> {
    /**
     * Creates a behavior for this actor. This method is called by the actor system when a new actor
     * is created.
     *
     * @param actorContext The context of the actor
     * @return A behavior for the actor
     */
    SpringActorBehavior<C> create(SpringActorContext actorContext);
}
```

### SpringShardedActor

The `SpringShardedActor` interface provides support for sharding actors across a cluster.

```java
public interface SpringShardedActor<T> {
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
     * Creates a fluent builder for spawning an actor.
     *
     * @param actorClass The class of the actor to spawn
     * @return A builder for configuring and spawning the actor
     */
    public <A extends SpringActorWithContext<C, ?>, C> SpringActorSpawnBuilder<A, C> actor(Class<A> actorClass);

    /**
     * Gets an existing actor or spawns a new one if it doesn't exist.
     * This is the recommended approach for most use cases.
     *
     * @param actorClass The class of the actor
     * @param actorId The ID of the actor
     * @return A CompletionStage that resolves to a reference to the actor
     */
    public <A extends SpringActorWithContext<C, ?>, C> CompletionStage<SpringActorRef<C>> getOrSpawn(
        Class<A> actorClass, String actorId);

    /**
     * Creates a fluent builder for getting a reference to a sharded actor.
     *
     * @param actorClass The class of the sharded actor
     * @return A builder for configuring and getting the sharded actor reference
     */
    public <T> SpringShardedActorBuilder<T> sharded(Class<? extends SpringShardedActor<T>> actorClass);
}
```

### SpringActorRef

The `SpringActorRef` class represents a reference to an actor. It provides methods for sending messages to the
actor.

```java
public class SpringActorRef<T> {
    /**
     * Asks the actor a question using an AskCommand and returns a builder for configuring
     * the ask operation. This method automatically injects the reply-to reference into the command.
     *
     * @param command The command that implements AskCommand
     * @return An AskBuilder for configuring and executing the ask operation
     */
    public <RES> AskBuilder<T, RES> ask(AskCommand<RES> command);

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
     * Asks the sharded actor a question using an AskCommand and returns a builder for configuring
     * the ask operation. This method automatically injects the reply-to reference into the command.
     *
     * @param command The command that implements AskCommand
     * @return An AskBuilder for configuring and executing the ask operation
     */
    public <RES> AskBuilder<T, RES> ask(AskCommand<RES> command);

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

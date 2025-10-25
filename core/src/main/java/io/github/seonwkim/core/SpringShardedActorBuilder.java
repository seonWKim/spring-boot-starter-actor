package io.github.seonwkim.core;

import io.github.seonwkim.core.shard.ShardedActor;
import io.github.seonwkim.core.shard.ShardedActorRegistry;
import org.apache.pekko.cluster.sharding.typed.javadsl.EntityRef;
import org.apache.pekko.cluster.sharding.typed.javadsl.EntityTypeKey;

/**
 * A fluent builder for creating references to sharded actors. This builder simplifies the process
 * of getting a reference to a sharded actor entity by providing a clean, fluent API.
 *
 * <p>Example usage:
 *
 * <pre>
 * var counter = actorSystem.sharded(CounterActor.class)
 *     .withId("counter-123")
 *     .get();
 * </pre>
 *
 * @param <T> The type of commands that the sharded actor can handle
 */
public class SpringShardedActorBuilder<T> {

    private final SpringActorSystem actorSystem;
    private String entityId;
    private EntityTypeKey<T> typeKey;

    /**
     * Creates a new SpringShardedActorBuilder.
     *
     * @param actorSystem The Spring actor system
     * @param actorClass The class of the sharded actor
     */
    public SpringShardedActorBuilder(SpringActorSystem actorSystem, Class<? extends ShardedActor<T>> actorClass) {
        this.actorSystem = actorSystem;
        this.typeKey = resolveTypeKey(actorClass);
    }

    /**
     * Sets the entity ID for the sharded actor reference.
     *
     * @param entityId The entity ID
     * @return This builder for method chaining
     */
    public SpringShardedActorBuilder<T> withId(String entityId) {
        if (entityId == null || entityId.trim().isEmpty()) {
            throw new IllegalArgumentException("Entity ID cannot be null or empty");
        }
        this.entityId = entityId;
        return this;
    }

    /**
     * Builds and returns the SpringShardedActorRef.
     *
     * @return A SpringShardedActorRef to the sharded actor entity
     * @throws IllegalStateException If the entity ID has not been set or if cluster sharding is not
     *     configured
     */
    public SpringShardedActorRef<T> get() {
        if (entityId == null) {
            throw new IllegalStateException("Entity ID must be set using withId() before calling get()");
        }

        if (actorSystem.getClusterSharding() == null) {
            throw new IllegalStateException("Cluster sharding not configured");
        }

        final EntityRef<T> entityRef = actorSystem.getClusterSharding().entityRefFor(typeKey, entityId);
        return new SpringShardedActorRef<>(actorSystem.getRaw().scheduler(), entityRef);
    }

    /**
     * Resolves the EntityTypeKey from the actor class using the ShardedActorRegistry. This method
     * retrieves the actor instance from the registry (which was populated during Spring startup)
     * and calls its typeKey() method. This approach is safer than reflection as it uses the
     * Spring-managed bean instance.
     *
     * <p>This method fails fast with clear error messages if:
     * <ul>
     *   <li>The ShardedActorRegistry is not available (incorrect SpringActorSystem setup)</li>
     *   <li>The actor class is not registered (missing @Component annotation)</li>
     * </ul>
     *
     * @param actorClass The sharded actor class
     * @return The resolved EntityTypeKey
     * @throws IllegalStateException If the registry is not available or the actor is not registered
     */
    private EntityTypeKey<T> resolveTypeKey(Class<? extends ShardedActor<T>> actorClass) {
        ShardedActorRegistry registry = actorSystem.getShardedActorRegistry();

        if (registry == null) {
            throw new IllegalStateException("ShardedActorRegistry is not available. "
                    + "Ensure you're using SpringActorSystemBuilder or Spring Boot auto-configuration with @EnableActorSupport.");
        }

        ShardedActor<T> actor = registry.getByClass(actorClass);
        if (actor == null) {
            throw new IllegalStateException("ShardedActor " + actorClass.getName() + " not found in registry. "
                    + "Ensure the actor is annotated with @Component and implements ShardedActor.");
        }

        return actor.typeKey();
    }
}

package io.github.seonwkim.core.shard;

import io.github.seonwkim.core.SpringActorSystem;
import javax.annotation.Nullable;
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

    @Nullable private String entityId;

    private EntityTypeKey<T> typeKey;

    /**
     * Creates a new SpringShardedActorBuilder.
     *
     * @param actorSystem The Spring actor system
     * @param actorClass The class of the sharded actor
     */
    public SpringShardedActorBuilder(SpringActorSystem actorSystem, Class<? extends SpringShardedActor<T>> actorClass) {
        if (actorSystem == null) {
            throw new IllegalArgumentException("actorSystem must not be null");
        }
        if (actorClass == null) {
            throw new IllegalArgumentException("actorClass must not be null");
        }
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
     * Builds and returns the SpringShardedActorHandle.
     *
     * @return A SpringShardedActorHandle to the sharded actor entity
     * @throws IllegalStateException If the entity ID has not been set or if cluster sharding is not
     *     configured
     */
    public SpringShardedActorHandle<T> get() {
        if (entityId == null) {
            throw new IllegalStateException("Entity ID must be set using withId() before calling get()");
        }

        if (actorSystem.getClusterSharding() == null) {
            throw new IllegalStateException("Cluster sharding not configured");
        }

        final EntityRef<T> entityRef = actorSystem.getClusterSharding().entityRefFor(typeKey, entityId);
        return new SpringShardedActorHandle<>(actorSystem.getRaw().scheduler(), entityRef);
    }

    /**
     * Resolves the EntityTypeKey from the actor class using the static ShardedActorRegistry.
     * This method retrieves the actor instance from the registry (which was populated via
     * BeanPostProcessor during Spring startup) and calls its typeKey() method.
     *
     * <p>This method fails fast with clear error messages if the actor class is not registered
     * (missing @Component annotation).
     *
     * @param actorClass The sharded actor class
     * @return The resolved EntityTypeKey
     * @throws IllegalStateException If the actor is not registered
     */
    private EntityTypeKey<T> resolveTypeKey(Class<? extends SpringShardedActor<T>> actorClass) {
        SpringShardedActor<T> actor = ShardedActorRegistry.getByClass(actorClass);
        if (actor == null) {
            throw new IllegalStateException("SpringShardedActor " + actorClass.getName() + " not found in registry. "
                    + "Ensure the actor is annotated with @Component and implements SpringShardedActor.");
        }

        return actor.typeKey();
    }
}

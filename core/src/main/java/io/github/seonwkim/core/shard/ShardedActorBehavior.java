package io.github.seonwkim.core.shard;

import java.util.function.Function;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.cluster.sharding.typed.javadsl.EntityContext;

/**
 * A behavior wrapper that provides framework-level features for sharded actors.
 *
 * <p>This class wraps Pekko's {@link Behavior} for sharded actors and provides a consistent
 * API with {@link io.github.seonwkim.core.SpringActorBehavior}, enabling future framework
 * extensions for sharded actors.
 *
 * <p>Use the fluent builder API to construct behaviors:
 * <pre>
 * {@code
 * @Override
 * public ShardedActorBehavior<Command> create(EntityContext<Command> entityContext) {
 *     return ShardedActorBehavior.builder(entityContext)
 *         .setup(ctx -> {
 *             // Use Pekko's standard Behaviors API
 *             return ShardedActorBehavior.of(Behaviors.receive(Command.class)
 *                 .onMessage(DoWork.class, this::handleDoWork)
 *                 .build());
 *         })
 *         .build();
 * }
 * }
 * </pre>
 *
 * <p><b>Future Extensibility:</b> While this class currently provides a simple wrapper,
 * it enables future framework features for sharded actors such as:
 * <ul>
 *   <li>Automatic passivation handling</li>
 *   <li>Shard-aware telemetry and metrics</li>
 *   <li>Cluster-aware framework commands</li>
 * </ul>
 *
 * @param <T> The message type this behavior handles
 */
public final class ShardedActorBehavior<T> {

    private final Behavior<T> behavior;

    private ShardedActorBehavior(Behavior<T> behavior) {
        this.behavior = behavior;
    }

    /**
     * Unwraps this ShardedActorBehavior to get the underlying Pekko Behavior.
     *
     * @return the underlying Pekko behavior
     */
    public Behavior<T> asBehavior() {
        return behavior;
    }

    /**
     * Creates a ShardedActorBehavior from a Pekko Behavior.
     *
     * <p>This is a simple wrapper for sharded actors.
     *
     * @param behavior the Pekko behavior to wrap
     * @param <T>      the message type
     * @return a new ShardedActorBehavior
     */
    public static <T> ShardedActorBehavior<T> of(Behavior<T> behavior) {
        return new ShardedActorBehavior<>(behavior);
    }

    /**
     * Creates a new builder for constructing a ShardedActorBehavior.
     *
     * @param entityContext the entity context from Pekko cluster sharding
     * @param <T>           the message type
     * @return a new builder instance
     */
    public static <T> Builder<T> builder(EntityContext<T> entityContext) {
        return new Builder<>(entityContext);
    }

    /**
     * Builder for creating ShardedActorBehavior instances with a fluent API.
     *
     * @param <T> the message type
     */
    public static final class Builder<T> {
        private final EntityContext<T> entityContext;
        private Function<EntityContext<T>, ShardedActorBehavior<T>> setupFunction;

        private Builder(EntityContext<T> entityContext) {
            this.entityContext = entityContext;
        }

        /**
         * Defines the behavior using a setup function.
         *
         * <p>The setup function receives the EntityContext and should return a
         * ShardedActorBehavior that defines how the actor processes messages.
         *
         * @param setupFunction the function to create the behavior
         * @return this builder for chaining
         */
        public Builder<T> setup(Function<EntityContext<T>, ShardedActorBehavior<T>> setupFunction) {
            this.setupFunction = setupFunction;
            return this;
        }

        /**
         * Builds the final ShardedActorBehavior.
         *
         * @return the constructed behavior
         */
        public ShardedActorBehavior<T> build() {
            if (setupFunction == null) {
                throw new IllegalStateException("setup() must be called before build()");
            }

            // Call the setup function with the entity context
            ShardedActorBehavior<T> userBehavior = setupFunction.apply(entityContext);

            // For now, just use the user behavior directly
            // Future framework features can be added here
            return userBehavior;
        }
    }
}

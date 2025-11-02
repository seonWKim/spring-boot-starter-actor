package io.github.seonwkim.core.shard;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.Signal;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.apache.pekko.actor.typed.javadsl.BehaviorBuilder;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
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
 * public SpringShardedActorBehavior<Command> create(EntityContext<Command> entityContext) {
 *     return SpringShardedActorBehavior.builder(Command.class, entityContext)
 *         .onMessage(DoWork.class, (ctx, msg) -> {
 *             ctx.getLog().info("Processing work for entity {}", entityContext.getEntityId());
 *             return Behaviors.same();
 *         })
 *         .build();
 * }
 * }
 * </pre>
 *
 * @param <T> The message type this behavior handles
 */
public final class SpringShardedActorBehavior<T> {

    private final Behavior<T> behavior;

    private SpringShardedActorBehavior(Behavior<T> behavior) {
        this.behavior = behavior;
    }

    /**
     * Unwraps this SpringShardedActorBehavior to get the underlying Pekko Behavior.
     *
     * @return the underlying Pekko behavior
     */
    public Behavior<T> asBehavior() {
        return behavior;
    }

    /**
     * Creates a new builder for constructing a SpringShardedActorBehavior.
     *
     * <p>The builder starts with ActorContext as the default state type. Use {@link Builder#onCreate(Function)}
     * to evolve the builder to use a custom state type that will be passed to message handlers.
     *
     * @param commandClass  the command class
     * @param entityContext the entity context from Pekko cluster sharding
     * @param <T>           the message type
     * @return a new builder instance with ActorContext as the state type
     */
    public static <T> Builder<T, ActorContext<T>> builder(Class<T> commandClass, EntityContext<T> entityContext) {
        return new Builder<>(commandClass, entityContext, ctx -> ctx);
    }

    /**
     * Builder for creating SpringShardedActorBehavior instances with a fluent API.
     *
     * <p>This builder supports type-safe state evolution through the {@link #onCreate(Function)} method.
     * The state type parameter {@code S} represents the type of object passed to message handlers.
     *
     * @param <T> the message type
     * @param <S> the state type passed to message handlers (defaults to ActorContext&lt;T&gt;)
     */
    public static final class Builder<T, S> {
        private final EntityContext<T> entityContext;
        private final Class<T> commandClass;
        private final Function<ActorContext<T>, S> onCreateCallback;
        private final List<MessageHandler<T, S, ?>> messageHandlers = new ArrayList<>();
        private final List<SignalHandler<T, S, ?>> signalHandlers = new ArrayList<>();

        private Builder(
                Class<T> commandClass, EntityContext<T> entityContext, Function<ActorContext<T>, S> onCreateCallback) {
            this.commandClass = commandClass;
            this.entityContext = entityContext;
            this.onCreateCallback = onCreateCallback;
        }

        /**
         * Evolves this builder to use a custom state type for message handlers.
         *
         * <p>This method allows you to create a state object during actor initialization that will be
         * passed to all message handlers. This is useful for encapsulating behavior logic in a separate class.
         *
         * <p>Example usage:
         * <pre>
         * {@code
         * return SpringShardedActorBehavior.builder(Command.class, entityContext)
         *     .onCreate(ctx -> new MyBehaviorHandler(ctx, entityContext))
         *     .onMessage(DoWork.class, (handler, msg) -> handler.handleWork(msg))
         *     .build();
         * }
         * </pre>
         *
         * @param callback the function that creates the state object from ActorContext
         * @param <NewS> the new state type
         * @return a new builder with the evolved state type
         */
        public <NewS> Builder<T, NewS> onCreate(Function<ActorContext<T>, NewS> callback) {
            return new Builder<>(commandClass, entityContext, callback);
        }

        /**
         * Adds a message handler for a specific message type.
         *
         * <p>The handler receives the state object (from onCreate) and the message.
         * If onCreate was not called, the state will be the ActorContext.
         *
         * @param type    the message class to handle
         * @param handler the handler function that receives state and message
         * @param <M>     the message type
         * @return this builder for chaining
         */
        public <M extends T> Builder<T, S> onMessage(Class<M> type, BiFunction<S, M, Behavior<T>> handler) {
            messageHandlers.add(new MessageHandler<>(type, handler));
            return this;
        }

        /**
         * Builds the final SpringShardedActorBehavior.
         *
         * @return the constructed behavior
         */
        public SpringShardedActorBehavior<T> build() {
            Behavior<T> userBehavior = Behaviors.setup(ctx -> {
                // Create the state object
                S state = onCreateCallback.apply(ctx);

                BehaviorBuilder<T> builder = Behaviors.receive(commandClass);

                // Add all message handlers - capture the returned builder each time
                for (MessageHandler<T, S, ?> handler : messageHandlers) {
                    builder = handler.addTo(builder, state);
                }

                // Add all signal handlers - capture the returned builder each time
                for (SignalHandler<T, S, ?> handler : signalHandlers) {
                    builder = handler.addTo(builder, state);
                }

                return builder.build();
            });

            return new SpringShardedActorBehavior<>(userBehavior);
        }

        /**
         * Internal class to store message handler information.
         */
        private static class MessageHandler<T, S, M extends T> {
            private final Class<M> type;
            private final BiFunction<S, M, Behavior<T>> handler;

            MessageHandler(Class<M> type, BiFunction<S, M, Behavior<T>> handler) {
                this.type = type;
                this.handler = handler;
            }

            BehaviorBuilder<T> addTo(BehaviorBuilder<T> builder, S state) {
                return builder.onMessage(type, msg -> handler.apply(state, msg));
            }
        }

        /**
         * Internal class to store signal handler information.
         */
        private static class SignalHandler<T, S, M extends Signal> {
            private final Class<M> type;
            private final BiFunction<S, M, Behavior<T>> handler;

            SignalHandler(Class<M> type, BiFunction<S, M, Behavior<T>> handler) {
                this.type = type;
                this.handler = handler;
            }

            BehaviorBuilder<T> addTo(BehaviorBuilder<T> builder, S state) {
                return builder.onSignal(type, sig -> handler.apply(state, sig));
            }
        }
    }
}

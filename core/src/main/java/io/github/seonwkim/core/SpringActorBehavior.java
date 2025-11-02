package io.github.seonwkim.core;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.Signal;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.apache.pekko.actor.typed.javadsl.BehaviorBuilder;
import org.apache.pekko.actor.typed.javadsl.Behaviors;

/**
 * A behavior wrapper that provides framework-level features for Spring actors.
 *
 * <p>This class wraps Pekko's {@link Behavior} and adds Spring actor framework capabilities
 * such as automatic handling of framework commands (e.g., spawning children with Spring DI).
 *
 * <p>Use the fluent builder API to construct behaviors:
 * <pre>
 * {@code
 * @Override
 * public SpringActorBehavior<Command> create(SpringActorContext actorContext) {
 *     return SpringActorBehavior.builder(Command.class, actorContext)
 *         .withFrameworkCommands()  // Opt-in to framework command handling
 *         .onMessage(DoWork.class, (ctx, msg) -> {
 *             ctx.getLog().info("Processing work");
 *             return Behaviors.same();
 *         })
 *         .build();
 * }
 * </pre>
 *
 * <p><b>Framework Commands:</b> When framework commands are enabled via
 * {@link Builder#withFrameworkCommands()}, the behavior will automatically handle:
 * <ul>
 *   <li>{@link FrameworkCommands.SpawnChild} - Spawn child actors with Spring DI</li>
 * </ul>
 *
 * <p><b>Zero Overhead:</b> If framework commands are not enabled, there is no performance
 * overhead - the user's behavior is used directly without any wrapping.
 *
 * @param <C> The command type this behavior handles
 * @see FrameworkCommand
 * @see FrameworkCommands
 */
public final class SpringActorBehavior<C> {

    private final Behavior<C> behavior;

    private SpringActorBehavior(Behavior<C> behavior) {
        this.behavior = behavior;
    }

    /**
     * Unwraps this SpringActorBehavior to get the underlying Pekko Behavior.
     *
     * @return the underlying Pekko behavior
     */
    public Behavior<C> asBehavior() {
        return behavior;
    }

    /**
     * Wraps a Pekko Behavior in a SpringActorBehavior.
     *
     * <p>Use this method when you need to wrap a behavior that has been enhanced
     * with Pekko features like supervision, timers, etc.
     *
     * @param behavior the Pekko behavior to wrap
     * @param <C>      the command type
     * @return a new SpringActorBehavior wrapping the given behavior
     */
    public static <C> SpringActorBehavior<C> wrap(Behavior<C> behavior) {
        return new SpringActorBehavior<>(behavior);
    }

    /**
     * Creates a new builder for constructing a SpringActorBehavior.
     *
     * <p>The builder starts with ActorContext as the default state type. Use {@link Builder#onCreate(Function)}
     * to evolve the builder to use a custom state type that will be passed to message handlers.
     *
     * @param commandClass the command class
     * @param actorContext the Spring actor context
     * @param <C>          the command type
     * @return a new builder instance with ActorContext as the state type
     */
    public static <C> Builder<C, ActorContext<C>> builder(Class<C> commandClass, SpringActorContext actorContext) {
        return new Builder<>(commandClass, actorContext, ctx -> ctx);
    }

    /**
     * Builder for creating SpringActorBehavior instances with a fluent API.
     *
     * <p>This builder supports type-safe state evolution through the {@link #onCreate(Function)} method.
     * The state type parameter {@code S} represents the type of object passed to message handlers.
     *
     * @param <C> the command type
     * @param <S> the state type passed to message handlers (defaults to ActorContext&lt;C&gt;)
     */
    public static final class Builder<C, S> {
        private final SpringActorContext actorContext;
        private final Class<C> commandClass;
        private final Function<ActorContext<C>, S> onCreateCallback;
        private final List<MessageHandler<C, S, ?>> messageHandlers = new ArrayList<>();
        private final List<SignalHandler<C, S, ?>> signalHandlers = new ArrayList<>();
        private boolean enableFrameworkCommands = false;

        private Builder(
                Class<C> commandClass, SpringActorContext actorContext, Function<ActorContext<C>, S> onCreateCallback) {
            this.commandClass = commandClass;
            this.actorContext = actorContext;
            this.onCreateCallback = onCreateCallback;
        }

        /**
         * Enables framework command handling.
         *
         * <p>When enabled, the behavior will automatically handle framework commands such as
         * {@link FrameworkCommands.SpawnChild} without requiring explicit message handlers.
         *
         * @return this builder for chaining
         */
        public Builder<C, S> withFrameworkCommands() {
            this.enableFrameworkCommands = true;
            return this;
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
         * return SpringActorBehavior.builder(Command.class, actorContext)
         *     .onCreate(ctx -> new MyBehaviorHandler(ctx, actorContext))
         *     .onMessage(DoWork.class, (handler, msg) -> handler.handleWork(msg))
         *     .build();
         * }
         * </pre>
         *
         * @param callback the function that creates the state object from ActorContext
         * @param <NewS> the new state type
         * @return a new builder with the evolved state type
         */
        public <NewS> Builder<C, NewS> onCreate(Function<ActorContext<C>, NewS> callback) {
            return new Builder<>(commandClass, actorContext, callback);
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
        public <M extends C> Builder<C, S> onMessage(Class<M> type, BiFunction<S, M, Behavior<C>> handler) {
            messageHandlers.add(new MessageHandler<>(type, handler));
            return this;
        }

        /**
         * Adds a signal handler for a specific signal type.
         *
         * <p>The handler receives the state object (from onCreate) and the signal.
         * If onCreate was not called, the state will be the ActorContext.
         *
         * @param type    the signal class to handle
         * @param handler the handler function that receives state and signal
         * @param <M>     the signal type
         * @return this builder for chaining
         */
        public <M extends Signal> Builder<C, S> onSignal(Class<M> type, BiFunction<S, M, Behavior<C>> handler) {
            signalHandlers.add(new SignalHandler<>(type, handler));
            return this;
        }

        /**
         * Builds the final SpringActorBehavior.
         *
         * @return the constructed behavior
         */
        public SpringActorBehavior<C> build() {
            Behavior<C> userBehavior = Behaviors.setup(ctx -> {
                // Create the state object
                S state = onCreateCallback.apply(ctx);

                BehaviorBuilder<C> builder = Behaviors.receive(commandClass);

                // Add all message handlers - capture the returned builder each time
                for (MessageHandler<C, S, ?> handler : messageHandlers) {
                    builder = handler.addTo(builder, state);
                }

                // Add all signal handlers - capture the returned builder each time
                for (SignalHandler<C, S, ?> handler : signalHandlers) {
                    builder = handler.addTo(builder, state);
                }

                return builder.build();
            });

            if (enableFrameworkCommands) {
                // Wrap userBehavior to intercept FrameworkCommand messages
                Behavior<C> wrappedBehavior = Behaviors.setup(ctx -> Behaviors.intercept(
                                () -> new FrameworkCommandInterceptor<>(ctx, actorContext), userBehavior));
                return new SpringActorBehavior<>(wrappedBehavior);
            }

            return new SpringActorBehavior<>(userBehavior);
        }

        /**
         * Internal class to store message handler information.
         */
        private static class MessageHandler<C, S, M extends C> {
            private final Class<M> type;
            private final BiFunction<S, M, Behavior<C>> handler;

            MessageHandler(Class<M> type, BiFunction<S, M, Behavior<C>> handler) {
                this.type = type;
                this.handler = handler;
            }

            BehaviorBuilder<C> addTo(BehaviorBuilder<C> builder, S state) {
                return builder.onMessage(type, msg -> handler.apply(state, msg));
            }
        }

        /**
         * Internal class to store signal handler information.
         */
        private static class SignalHandler<C, S, M extends Signal> {
            private final Class<M> type;
            private final BiFunction<S, M, Behavior<C>> handler;

            SignalHandler(Class<M> type, BiFunction<S, M, Behavior<C>> handler) {
                this.type = type;
                this.handler = handler;
            }

            BehaviorBuilder<C> addTo(BehaviorBuilder<C> builder, S state) {
                return builder.onSignal(type, sig -> handler.apply(state, sig));
            }
        }
    }
}

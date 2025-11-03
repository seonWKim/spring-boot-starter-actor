package io.github.seonwkim.core;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.Signal;
import org.apache.pekko.actor.typed.SupervisorStrategy;
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
 *         .onMessage(DoWork.class, (ctx, msg) -> {
 *             ctx.getLog().info("Processing work");
 *             return Behaviors.same();
 *         })
 *         .build();
 * }
 * </pre>
 *
 * <p><b>Framework Commands:</b> Framework command handling is automatically enabled when your
 * Command interface extends {@link FrameworkCommand}. When enabled, the behavior will automatically handle:
 * <ul>
 *   <li>{@link FrameworkCommands.SpawnChild} - Spawn child actors with Spring DI</li>
 *   <li>{@link FrameworkCommands.GetChild} - Get reference to existing child actors</li>
 *   <li>{@link FrameworkCommands.ExistsChild} - Check if child actor exists</li>
 * </ul>
 *
 * <p><b>Zero Overhead:</b> If your Command interface does not extend {@link FrameworkCommand},
 * there is no performance overhead - the user's behavior is used directly without any wrapping.
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
        private SupervisorStrategy supervisionStrategy = null;

        private Builder(
                Class<C> commandClass, SpringActorContext actorContext, Function<ActorContext<C>, S> onCreateCallback) {
            this.commandClass = commandClass;
            this.actorContext = actorContext;
            this.onCreateCallback = onCreateCallback;

            // Auto-detect: if Command extends FrameworkCommand, automatically enable framework commands
            this.enableFrameworkCommands = FrameworkCommand.class.isAssignableFrom(commandClass);
        }

        /**
         * Sets the supervision strategy for this actor.
         *
         * <p>The supervision strategy determines how the actor should be supervised by its parent
         * when failures occur. Common strategies include restart, stop, and resume.
         *
         * <p>Example usage:
         * <pre>
         * {@code
         * .withSupervisionStrategy(SupervisorStrategy.restart().withLimit(10, Duration.ofMinutes(1)))
         * }
         * </pre>
         *
         * @param strategy the supervision strategy to apply
         * @return this builder for chaining
         */
        public Builder<C, S> withSupervisionStrategy(SupervisorStrategy strategy) {
            this.supervisionStrategy = strategy;
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
            Builder<C, NewS> newBuilder = new Builder<>(commandClass, actorContext, callback);
            newBuilder.enableFrameworkCommands = this.enableFrameworkCommands;
            newBuilder.supervisionStrategy = this.supervisionStrategy;
            return newBuilder;
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
            if (enableFrameworkCommands) {
                // Wrap with framework command handling
                Behavior<C> behaviorWithFramework = Behaviors.setup(ctx -> {
                    // Create the state object
                    S state = onCreateCallback.apply(ctx);

                    Behavior<C> behavior = createFrameworkCommandHandlingBehavior(ctx, state);

                    // Apply supervision strategy if provided
                    if (supervisionStrategy != null) {
                        behavior = Behaviors.supervise(behavior).onFailure(supervisionStrategy);
                    }

                    return behavior;
                });
                return new SpringActorBehavior<>(behaviorWithFramework);
            } else {
                // No framework commands - just create the user behavior
                Behavior<C> userBehavior = Behaviors.setup(ctx -> {
                    // Create the state object
                    S state = onCreateCallback.apply(ctx);

                    BehaviorBuilder<C> builder = Behaviors.receive(commandClass);

                    // Add all message handlers
                    for (MessageHandler<C, S, ?> handler : messageHandlers) {
                        builder = handler.addTo(builder, state);
                    }

                    // Add all signal handlers
                    for (SignalHandler<C, S, ?> handler : signalHandlers) {
                        builder = handler.addTo(builder, state);
                    }

                    Behavior<C> behavior = builder.build();

                    // Apply supervision strategy if provided
                    if (supervisionStrategy != null) {
                        behavior = Behaviors.supervise(behavior).onFailure(supervisionStrategy);
                    }

                    return behavior;
                });
                return new SpringActorBehavior<>(userBehavior);
            }
        }

        /**
         * Creates a behavior that intercepts framework commands before delegating to user handlers.
         */
        @SuppressWarnings("unchecked")
        private Behavior<C> createFrameworkCommandHandlingBehavior(ActorContext<C> ctx, S state) {
            // Use Object.class to receive both framework commands and user commands
            return Behaviors.receive(Object.class)
                    .onMessage(Object.class, msg -> {
                        // Handle framework commands first
                        if (msg instanceof FrameworkCommands.SpawnChild) {
                            handleSpawnChild(ctx, (FrameworkCommands.SpawnChild<?>) msg);
                            return Behaviors.same();
                        }
                        if (msg instanceof FrameworkCommands.GetChild) {
                            handleGetChild(ctx, (FrameworkCommands.GetChild<?>) msg);
                            return Behaviors.same();
                        }
                        if (msg instanceof FrameworkCommands.ExistsChild) {
                            handleExistsChild(ctx, (FrameworkCommands.ExistsChild<?>) msg);
                            return Behaviors.same();
                        }

                        // If it's a user command, handle it
                        if (commandClass.isInstance(msg)) {
                            C typedMsg = (C) msg;
                            // Try each user message handler
                            for (MessageHandler<C, S, ?> handler : messageHandlers) {
                                Behavior<C> result = handler.tryHandle(typedMsg, state);
                                if (result != null) {
                                    return (Behavior<Object>) (Behavior<?>) result;
                                }
                            }
                        }

                        return Behaviors.unhandled();
                    })
                    .onSignal(Signal.class, sig -> {
                        // Try each signal handler
                        for (SignalHandler<C, S, ?> handler : signalHandlers) {
                            Behavior<C> result = handler.tryHandle(sig, state);
                            if (result != null) {
                                return (Behavior<Object>) (Behavior<?>) result;
                            }
                        }
                        return Behaviors.unhandled();
                    })
                    .build()
                    .narrow();
        }

        /**
         * Handles SpawnChild framework command.
         */
        @SuppressWarnings("unchecked")
        private <CC> void handleSpawnChild(ActorContext<C> ctx, FrameworkCommands.SpawnChild<CC> msg) {
            try {
                ActorTypeRegistry registry = actorContext.registry();
                if (registry == null) {
                    msg.replyTo.tell(
                            FrameworkCommands.SpawnChildResponse.failure(
                                    "ActorTypeRegistry is null. Ensure SpringActorContext.registry() returns a non-null registry."));
                    return;
                }

                // Build child context and name
                SpringActorContext childContext = msg.childContext;
                String childName = buildChildName(msg.actorClass, childContext.actorId());

                // Check if child already exists
                if (ctx.getChild(childName).isPresent()) {
                    ActorRef<CC> existingChild =
                            (ActorRef<CC>) ctx.getChild(childName).get();
                    msg.replyTo.tell(FrameworkCommands.SpawnChildResponse.alreadyExists(existingChild));
                    return;
                }

                // Create behavior using Spring DI
                Behavior<CC> behavior = (Behavior<CC>)
                        registry.createBehavior(msg.actorClass, childContext).asBehavior();

                // Apply supervision if provided
                if (msg.strategy != null) {
                    behavior = Behaviors.supervise(behavior).onFailure(msg.strategy);
                }

                // Spawn the child
                ActorRef<CC> childRef = ctx.spawn(behavior, childName);
                msg.replyTo.tell(FrameworkCommands.SpawnChildResponse.success(childRef));

            } catch (Exception e) {
                ctx.getLog().error("Failed to spawn child actor", e);
                msg.replyTo.tell(
                        FrameworkCommands.SpawnChildResponse.failure("Failed to spawn child: " + e.getMessage()));
            }
        }

        /**
         * Handles GetChild framework command.
         */
        @SuppressWarnings("unchecked")
        private <CC> void handleGetChild(ActorContext<C> ctx, FrameworkCommands.GetChild<CC> msg) {
            String childName = buildChildName(msg.actorClass, msg.childId);

            if (ctx.getChild(childName).isPresent()) {
                ActorRef<CC> childRef = (ActorRef<CC>) ctx.getChild(childName).get();
                msg.replyTo.tell(FrameworkCommands.GetChildResponse.found(childRef));
            } else {
                msg.replyTo.tell(FrameworkCommands.GetChildResponse.notFound());
            }
        }

        /**
         * Handles ExistsChild framework command.
         */
        private void handleExistsChild(ActorContext<C> ctx, FrameworkCommands.ExistsChild<?> msg) {
            String childName = buildChildName(msg.actorClass, msg.childId);

            if (ctx.getChild(childName).isPresent()) {
                msg.replyTo.tell(FrameworkCommands.ExistsChildResponse.exists());
            } else {
                msg.replyTo.tell(FrameworkCommands.ExistsChildResponse.notExists());
            }
        }

        /**
         * Builds a unique child name from the actor class and ID.
         */
        private static String buildChildName(Class<?> actorClass, String actorId) {
            return actorClass.getName() + ":" + actorId;
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

            @SuppressWarnings("unchecked")
            Behavior<C> tryHandle(C msg, S state) {
                if (type.isInstance(msg)) {
                    return handler.apply(state, (M) msg);
                }
                return null;
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

            @SuppressWarnings("unchecked")
            Behavior<C> tryHandle(Signal sig, S state) {
                if (type.isInstance(sig)) {
                    return handler.apply(state, (M) sig);
                }
                return null;
            }
        }
    }
}

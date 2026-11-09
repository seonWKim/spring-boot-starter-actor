package io.github.seonwkim.actor;

import io.github.seonwkim.actor.router.RouterPool;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;
import javax.annotation.Nullable;

/**
 * How an actor handles its messages and signals.
 *
 * <p>A message handler returns the actor's next behavior: {@link #same()} to keep handling
 * messages the same way, {@link #stopped()} to stop, {@link #unhandled()} when it didn't handle the
 * message, or a newly built behavior to change how the actor handles the next message.
 *
 * <p>Use the fluent builder API to construct behaviors:
 * <pre>
 * {@code
 * @Override
 * public SpringActorBehavior<Command> create(SpringActorContext actorContext) {
 *     return SpringActorBehavior.builder(Command.class, actorContext)
 *         .onMessage(DoWork.class, (ctx, msg) -> {
 *             ctx.getLog().info("Processing work");
 *             return SpringActorBehavior.same();
 *         })
 *         .build();
 * }
 * }
 * </pre>
 *
 * <p>You can use {@code .withState()} to create a custom state object that encapsulates your behavior logic:
 * <pre>
 * {@code
 * return SpringActorBehavior.builder(Command.class, actorContext)
 *     .withState(ctx -> new MyBehaviorHandler(ctx, actorContext))
 *     .onMessage(DoWork.class, (handler, msg) -> handler.handleWork(msg))
 *     .build();
 * }
 * </pre>
 *
 * <p><b>Framework Commands:</b> Framework command handling is automatically enabled when your
 * Command interface extends {@link FrameworkCommand}. When enabled, the behavior will automatically handle:
 * spawning child actors with Spring DI, getting an existing child, and checking whether a child exists.
 *
 * @param <C> The command type this behavior handles
 * @see FrameworkCommand
 */
public final class SpringActorBehavior<C> {

    /** What a behavior is. Read by the actor engine; application code doesn't need it. */
    public enum Kind {
        /** Keep the current behavior. */
        SAME,
        /** Stop the actor. */
        STOPPED,
        /** The message wasn't handled; the current behavior stays. */
        UNHANDLED,
        /** A behavior built with {@link #builder}: see {@link #definition()}. */
        DEFINED,
        /** A behavior of the underlying engine, passed to {@link #wrap(Object)}. */
        NATIVE,
        /** A pool of workers built with {@code SpringRouterBehavior}: see {@link #routerPool()}. */
        ROUTER
    }

    private static final SpringActorBehavior<?> SAME = new SpringActorBehavior<>(Kind.SAME, null, null, null);
    private static final SpringActorBehavior<?> STOPPED = new SpringActorBehavior<>(Kind.STOPPED, null, null, null);
    private static final SpringActorBehavior<?> UNHANDLED = new SpringActorBehavior<>(Kind.UNHANDLED, null, null, null);

    private final Kind kind;

    @Nullable private final Definition<C, ?> definition;

    @Nullable private final Object nativeBehavior;

    @Nullable private final RouterPool<C> routerPool;

    private SpringActorBehavior(
            Kind kind,
            @Nullable Definition<C, ?> definition,
            @Nullable Object nativeBehavior,
            @Nullable RouterPool<C> routerPool) {
        this.kind = kind;
        this.definition = definition;
        this.nativeBehavior = nativeBehavior;
        this.routerPool = routerPool;
    }

    /** Keep handling messages the same way. */
    @SuppressWarnings("unchecked")
    public static <C> SpringActorBehavior<C> same() {
        return (SpringActorBehavior<C>) SAME;
    }

    /** Stop the actor. Its {@link PostStop} handler runs. */
    @SuppressWarnings("unchecked")
    public static <C> SpringActorBehavior<C> stopped() {
        return (SpringActorBehavior<C>) STOPPED;
    }

    /** The message wasn't handled: it is published as unhandled and the current behavior stays. */
    @SuppressWarnings("unchecked")
    public static <C> SpringActorBehavior<C> unhandled() {
        return (SpringActorBehavior<C>) UNHANDLED;
    }

    /**
     * Escape hatch: use a behavior written directly against the underlying engine (a Pekko
     * {@code org.apache.pekko.actor.typed.Behavior<C>}). Prefer {@link #builder}: such a behavior
     * gets none of the starter's features (framework commands, MDC, metrics hooks).
     *
     * @throws IllegalArgumentException when the engine can't run it (checked when the actor starts)
     */
    public static <C> SpringActorBehavior<C> wrap(Object engineBehavior) {
        return new SpringActorBehavior<>(
                Kind.NATIVE, null, Objects.requireNonNull(engineBehavior, "engineBehavior"), null);
    }

    /** A router; {@code SpringRouterBehavior.builder(...).build()} is the way to get a {@link RouterPool}. */
    public static <C> SpringActorBehavior<C> router(RouterPool<C> pool) {
        return new SpringActorBehavior<>(Kind.ROUTER, null, null, Objects.requireNonNull(pool, "pool"));
    }

    /**
     * Starts building a behavior.
     *
     * @param commandClass the actor's command type
     * @param actorContext the context the actor was spawned with
     */
    public static <C> Builder<C, SpringBehaviorContext<C>> builder(
            Class<C> commandClass, SpringActorContext actorContext) {
        return new Builder<>(commandClass, actorContext, ctx -> ctx, null, null);
    }

    public Kind kind() {
        return kind;
    }

    /** The built behavior when {@link #kind()} is {@link Kind#DEFINED}. */
    public Definition<C, ?> definition() {
        if (definition == null) {
            throw new IllegalStateException("Not a built behavior: " + kind);
        }
        return definition;
    }

    /** The pool when {@link #kind()} is {@link Kind#ROUTER}. */
    public RouterPool<C> routerPool() {
        if (routerPool == null) {
            throw new IllegalStateException("Not a router: " + kind);
        }
        return routerPool;
    }

    /** The engine behavior when {@link #kind()} is {@link Kind#NATIVE}. */
    public Object nativeBehavior() {
        if (nativeBehavior == null) {
            throw new IllegalStateException("Not an engine behavior: " + kind);
        }
        return nativeBehavior;
    }

    /**
     * A built behavior, as data. Read by the actor engine; application code doesn't need it.
     *
     * @param frameworkCommands whether the command type extends {@link FrameworkCommand}
     */
    public record Definition<C, S>(
            Class<C> commandClass,
            SpringActorContext actorContext,
            Function<SpringBehaviorContext<C>, S> stateFactory,
            List<MessageHandler<C, S, ?>> messageHandlers,
            List<SignalHandler<C, S, ?>> signalHandlers,
            @Nullable SupervisionStrategy supervisionStrategy,
            @Nullable Function<C, Map<String, String>> mdcForMessage,
            boolean frameworkCommands) {
        public Definition {
            messageHandlers = List.copyOf(messageHandlers);
            signalHandlers = List.copyOf(signalHandlers);
        }
    }

    /** A message handler of a {@link Definition}. */
    public record MessageHandler<C, S, M extends C>(Class<M> type, BiFunction<S, M, SpringActorBehavior<C>> handler) {
        /** The next behavior, or null when the message is not of this handler's type. */
        @SuppressWarnings("unchecked")
        public @Nullable SpringActorBehavior<C> tryHandle(S state, Object message) {
            return type.isInstance(message) ? handler.apply(state, (M) message) : null;
        }
    }

    /** A signal handler of a {@link Definition}. */
    public record SignalHandler<C, S, M extends ActorSignal>(
            Class<M> type, BiFunction<S, M, SpringActorBehavior<C>> handler) {
        /** The next behavior, or null when the signal is not of this handler's type. */
        @SuppressWarnings("unchecked")
        public @Nullable SpringActorBehavior<C> tryHandle(S state, ActorSignal signal) {
            return type.isInstance(signal) ? handler.apply(state, (M) signal) : null;
        }
    }

    public static final class Builder<C, S> {
        private final Class<C> commandClass;
        private final SpringActorContext actorContext;
        private final Function<SpringBehaviorContext<C>, S> stateFactory;
        private final List<MessageHandler<C, S, ?>> messageHandlers = new ArrayList<>();
        private final List<SignalHandler<C, S, ?>> signalHandlers = new ArrayList<>();

        @Nullable private SupervisionStrategy supervisionStrategy;

        @Nullable private Function<C, Map<String, String>> mdcForMessage;

        private Builder(
                Class<C> commandClass,
                SpringActorContext actorContext,
                Function<SpringBehaviorContext<C>, S> stateFactory,
                @Nullable SupervisionStrategy supervisionStrategy,
                @Nullable Function<C, Map<String, String>> mdcForMessage) {
            this.commandClass = Objects.requireNonNull(commandClass, "commandClass");
            this.actorContext = Objects.requireNonNull(actorContext, "actorContext");
            this.stateFactory = stateFactory;
            this.supervisionStrategy = supervisionStrategy;
            this.mdcForMessage = mdcForMessage;
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
         * .withSupervisionStrategy(SupervisionStrategy.restart().withLimit(10, Duration.ofMinutes(1)))
         * }
         * </pre>
         *
         * @param strategy the supervision strategy to apply
         * @return this builder for chaining
         */
        public Builder<C, S> withSupervisionStrategy(SupervisionStrategy strategy) {
            this.supervisionStrategy = Objects.requireNonNull(strategy, "strategy");
            return this;
        }

        /**
         * Configures the builder to use a custom state type for message handlers.
         *
         * <p>This method evolves the builder's type to work with a custom state object that will be
         * created during actor initialization and passed to all message handlers. This is useful for
         * encapsulating behavior logic in a separate class or storing actor-specific data.
         *
         * <p>Example usage:
         * <pre>
         * {@code
         * return SpringActorBehavior.builder(Command.class, actorContext)
         *     .withState(ctx -> new MyBehaviorHandler(ctx, actorContext))
         *     .onMessage(DoWork.class, (handler, msg) -> handler.handleWork(msg))
         *     .build();
         * }
         * </pre>
         *
         * @param stateFactory the function that creates the state object from SpringBehaviorContext
         * @param <NewS> the new state type
         * @return a new builder with the evolved state type
         */
        public <NewS> Builder<C, NewS> withState(Function<SpringBehaviorContext<C>, NewS> stateFactory) {
            return new Builder<>(commandClass, actorContext, stateFactory, supervisionStrategy, mdcForMessage);
        }

        /**
         * Adds a message handler for a specific message type.
         *
         * <p>The handler receives the state object (from withState) and the message.
         * If withState was not called, the state will be the ActorContext.
         *
         * @param type    the message class to handle
         * @param handler the handler function that receives state and message
         * @param <M>     the message type
         * @return this builder for chaining
         */
        public <M extends C> Builder<C, S> onMessage(Class<M> type, BiFunction<S, M, SpringActorBehavior<C>> handler) {
            messageHandlers.add(new MessageHandler<>(type, handler));
            return this;
        }

        /**
         * Adds a signal handler for a specific signal type.
         *
         * <p>The handler receives the state object (from withState) and the signal.
         * If withState was not called, the state will be the ActorContext.
         *
         * @param type    the signal class to handle: {@link PreRestart} or {@link PostStop}
         * @param handler the handler function that receives state and signal
         * @param <M>     the signal type
         * @return this builder for chaining
         */
        public <M extends ActorSignal> Builder<C, S> onSignal(
                Class<M> type, BiFunction<S, M, SpringActorBehavior<C>> handler) {
            signalHandlers.add(new SignalHandler<>(type, handler));
            return this;
        }

        /**
         * Configures dynamic MDC (Mapped Diagnostic Context) values that are computed per message.
         *
         * <p>MDC allows you to add contextual information to log entries. This method configures
         * dynamic MDC values that are computed for each incoming message. These will be combined
         * with any static MDC values configured via {@link SpringActorSpawnBuilder#withMdc(MdcConfig)}.
         *
         * <p>Example usage:
         * <pre>{@code
         * SpringActorBehavior.builder(Command.class, actorContext)
         *     .withMdc(msg -> Map.of(
         *         "messageType", msg.getClass().getSimpleName(),
         *         "messageId", msg.getId(),
         *         "timestamp", String.valueOf(System.currentTimeMillis())
         *     ))
         *     .onMessage(ProcessOrder.class, (ctx, msg) -> {
         *         // MDC automatically includes messageType, messageId, timestamp
         *         ctx.getLog().info("Processing order");
         *         return SpringActorBehavior.same();
         *     })
         *     .build();
         * }</pre>
         *
         * <p>The function receives each message and should return a Map of MDC key-value pairs
         * to be added to the logging context for that message.
         *
         * @param mdcForMessage A function that computes MDC values from a message
         * @return this builder for chaining
         */
        public Builder<C, S> withMdc(Function<C, Map<String, String>> mdcForMessage) {
            this.mdcForMessage = mdcForMessage;
            return this;
        }

        /**
         * Builds the final SpringActorBehavior.
         *
         * @return the constructed behavior
         */
        public SpringActorBehavior<C> build() {
            return new SpringActorBehavior<>(
                    Kind.DEFINED,
                    new Definition<>(
                            commandClass,
                            actorContext,
                            stateFactory,
                            messageHandlers,
                            signalHandlers,
                            supervisionStrategy,
                            mdcForMessage,
                            FrameworkCommand.class.isAssignableFrom(commandClass)),
                    null,
                    null);
        }
    }
}

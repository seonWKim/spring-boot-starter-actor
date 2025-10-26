package io.github.seonwkim.core;

import java.time.Duration;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

import org.apache.pekko.actor.PoisonPill;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.actor.typed.Scheduler;
import org.apache.pekko.actor.typed.javadsl.AskPattern;
import org.apache.pekko.japi.function.Function;

/**
 * A wrapper around Pekko's ActorRef that provides methods for asking and telling messages to an
 * actor. This class simplifies interaction with actors by providing a more Spring-friendly API.
 *
 * @param <T> The type of messages that the actor can handle
 */
public class SpringActorRef<T> {

    private final Scheduler scheduler;
    private final ActorRef<T> actorRef;
    private final Duration defaultTimeout;
    private final ActorSystem<RootGuardian.Command> actorSystem;
    private final Class<? extends SpringActorWithContext<?, T, ?>> actorClass;
    private final SpringActorContext actorContext;

    /**
     * Creates a builder for SpringActorRef.
     *
     * @param scheduler The scheduler to use for asking messages
     * @param actorRef The actor reference to wrap
     * @param <T> The type of messages that the actor can handle
     * @return A new builder for SpringActorRef
     */
    public static <T> SpringActorRefBuilder<T> builder(Scheduler scheduler, ActorRef<T> actorRef) {
        return new SpringActorRefBuilder<>(scheduler, actorRef);
    }

    /** Default value for the default timeout in seconds. */
    public static final int DEFAULT_TIMEOUT_SECONDS = 3;

    /**
     * Creates a new SpringActorRef with the given scheduler and actor reference. This constructor is
     * deprecated in favor of the constructor with metadata.
     *
     * @param scheduler The scheduler to use for asking messages
     * @param actorRef The actor reference to wrap
     * @deprecated Use the constructor with actor metadata instead
     */
    @Deprecated
    public SpringActorRef(Scheduler scheduler, ActorRef<T> actorRef) {
        this(scheduler, actorRef, Duration.ofSeconds(DEFAULT_TIMEOUT_SECONDS));
    }

    /**
     * Creates a new SpringActorRef with the given scheduler, actor reference, and default timeout.
     * This constructor is deprecated in favor of the constructor with metadata.
     *
     * @param scheduler The scheduler to use for asking messages
     * @param actorRef The actor reference to wrap
     * @param defaultTimeout The default timeout for ask operations
     * @deprecated Use the constructor with actor metadata instead
     */
    @Deprecated
    public SpringActorRef(Scheduler scheduler, ActorRef<T> actorRef, Duration defaultTimeout) {
        this(scheduler, actorRef, defaultTimeout, null, null, null);
    }

    /**
     * Creates a new SpringActorRef with full metadata for enhanced operations.
     *
     * @param scheduler The scheduler to use for asking messages
     * @param actorRef The actor reference to wrap
     * @param defaultTimeout The default timeout for ask operations
     * @param actorSystem The actor system (can be null for legacy compatibility)
     * @param actorClass The actor class (can be null for legacy compatibility)
     * @param actorContext The actor context (can be null for legacy compatibility)
     */
    public SpringActorRef(
            Scheduler scheduler,
            ActorRef<T> actorRef,
            Duration defaultTimeout,
            ActorSystem<RootGuardian.Command> actorSystem,
            Class<? extends SpringActorWithContext<?, T, ?>> actorClass,
            SpringActorContext actorContext) {
        this.scheduler = scheduler;
        this.actorRef = actorRef;
        this.defaultTimeout = defaultTimeout;
        this.actorSystem = actorSystem;
        this.actorClass = actorClass;
        this.actorContext = actorContext;
    }

    /**
     * Asks the actor a question and expects a response, using the default timeout. This method sends
     * a message to the actor and returns a CompletionStage that will be completed with the response.
     *
     * @param messageFactory A function that creates a message given a reply-to actor reference
     * @param <REQ> The type of the request message
     * @param <RES> The type of the response message
     * @return A CompletionStage that will be completed with the response
     */
    public <REQ extends T, RES> CompletionStage<RES> ask(Function<ActorRef<RES>, REQ> messageFactory) {
        return ask(messageFactory, defaultTimeout);
    }

    /**
     * Asks the actor a question and expects a response. This method sends a message to the actor and
     * returns a CompletionStage that will be completed with the response.
     *
     * @param messageFactory A function that creates a message given a reply-to actor reference
     * @param timeout The maximum time to wait for a response
     * @param <REQ> The type of the request message
     * @param <RES> The type of the response message
     * @return A CompletionStage that will be completed with the response
     */
    public <REQ extends T, RES> CompletionStage<RES> ask(
            Function<ActorRef<RES>, REQ> messageFactory, Duration timeout) {
        return AskPattern.ask(actorRef, messageFactory::apply, timeout, scheduler);
    }

    /**
     * Creates a fluent builder for asking the actor a question with advanced options.
     * This builder allows setting timeout, timeout handlers, and error handlers.
     *
     * <p>Example usage:
     * <pre>
     * CompletionStage&lt;String&gt; result = actor
     *     .askBuilder(GetValue::new)
     *     .withTimeout(Duration.ofSeconds(5))
     *     .onTimeout(() -&gt; "default-value")
     *     .execute();
     * </pre>
     *
     * @param messageFactory A function that creates a message given a reply-to actor reference
     * @param <REQ> The type of the request message
     * @param <RES> The type of the response message
     * @return A new AskBuilder for fluent configuration
     */
    @SuppressWarnings("unchecked")
    public <REQ extends T, RES> AskBuilder<REQ, RES> askBuilder(Function<ActorRef<RES>, REQ> messageFactory) {
        return new AskBuilder<>(messageFactory, (ActorRef<REQ>) actorRef, scheduler, defaultTimeout);
    }

    /**
     * Sends a message to the actor without expecting a response.
     *
     * @param message The message to send
     */
    public void tell(T message) {
        actorRef.tell(message);
    }

    /**
     * Returns the underlying actor reference.
     *
     * @return The underlying Pekko ActorRef
     */
    public ActorRef<T> getUnderlying() {
        return actorRef;
    }

    /**
     * Stops this actor by sending a PoisonPill message. This is a convenience method that terminates
     * the actor gracefully after processing all messages currently in its mailbox.
     *
     * <p>Note: This method does not wait for the actor to terminate. The actor will stop
     * asynchronously after processing pending messages.
     */
    @SuppressWarnings("unchecked")
    public void stop() {
        actorRef.tell((T) PoisonPill.getInstance());
    }

    /**
     * Fluent builder for asking the actor a question with advanced configuration options.
     * This builder provides a more flexible API compared to the simple ask() methods,
     * allowing configuration of timeouts and error handling.
     *
     * @param <REQ> The type of the request message
     * @param <RES> The type of the response message
     */
    public static class AskBuilder<REQ, RES> {
        private final Function<ActorRef<RES>, REQ> messageFactory;
        private final ActorRef<REQ> actorRef;
        private final Scheduler scheduler;
        private Duration timeout;
        private Supplier<RES> timeoutHandler;

        /**
         * Creates a new AskBuilder.
         *
         * @param messageFactory The message factory function
         * @param actorRef The actor reference to ask
         * @param scheduler The scheduler for the ask pattern
         * @param defaultTimeout The default timeout
         */
        AskBuilder(
                Function<ActorRef<RES>, REQ> messageFactory,
                ActorRef<REQ> actorRef,
                Scheduler scheduler,
                Duration defaultTimeout) {
            this.messageFactory = messageFactory;
            this.actorRef = actorRef;
            this.scheduler = scheduler;
            this.timeout = defaultTimeout;
        }

        /**
         * Sets the timeout for this ask operation.
         *
         * @param timeout The maximum time to wait for a response
         * @return This builder for method chaining
         */
        public AskBuilder<REQ, RES> withTimeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        /**
         * Sets a handler that provides a default value if the ask operation times out.
         * If this handler is set, timeout exceptions will be caught and replaced with
         * the default value provided by the supplier.
         *
         * @param defaultValueSupplier A supplier that provides the default value on timeout
         * @return This builder for method chaining
         */
        public AskBuilder<REQ, RES> onTimeout(Supplier<RES> defaultValueSupplier) {
            this.timeoutHandler = defaultValueSupplier;
            return this;
        }

        /**
         * Executes the ask operation with the configured options.
         *
         * @return A CompletionStage that will be completed with the response, or with the
         *         default value if a timeout occurs and a timeout handler was configured
         */
        public CompletionStage<RES> execute() {
            CompletionStage<RES> result = AskPattern.ask(actorRef, messageFactory::apply, timeout, scheduler);

            // Apply timeout handler if configured
            if (timeoutHandler != null) {
                result = result.exceptionally(throwable -> {
                    if (throwable instanceof TimeoutException
                            || (throwable.getCause() != null && throwable.getCause() instanceof TimeoutException)) {
                        return timeoutHandler.get();
                    }
                    // Re-throw non-timeout exceptions
                    if (throwable instanceof RuntimeException) {
                        throw (RuntimeException) throwable;
                    }
                    throw new RuntimeException(throwable);
                });
            }

            return result;
        }
    }
}

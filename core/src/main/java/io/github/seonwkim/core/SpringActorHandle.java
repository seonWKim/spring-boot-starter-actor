package io.github.seonwkim.core;

import java.time.Duration;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import org.apache.pekko.actor.PoisonPill;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Scheduler;
import org.apache.pekko.actor.typed.javadsl.AskPattern;
import org.apache.pekko.japi.function.Function;

/**
 * A wrapper around Pekko's ActorRef that provides methods for asking and telling messages to an
 * actor. This class simplifies interaction with actors by providing a more Spring-friendly API.
 *
 * @param <T> The type of messages that the actor can handle
 */
public class SpringActorHandle<T> {

    private final Scheduler scheduler;
    private final ActorRef<T> actorRef;
    private final Duration defaultTimeout;

    /**
     * Creates a builder for SpringActorHandle.
     *
     * @param scheduler The scheduler to use for asking messages
     * @param actorRef The actor reference to wrap
     * @param <T> The type of messages that the actor can handle
     * @return A new builder for SpringActorHandle
     */
    public static <T> SpringActorRefBuilder<T> builder(Scheduler scheduler, ActorRef<T> actorRef) {
        return new SpringActorRefBuilder<>(scheduler, actorRef);
    }

    /** Default value for the default timeout in seconds. */
    public static final int DEFAULT_TIMEOUT_SECONDS = ActorConstants.DEFAULT_TIMEOUT_SECONDS;

    /**
     * Creates a new SpringActorHandle with the given scheduler and actor reference.
     *
     * @param scheduler The scheduler to use for asking messages
     * @param actorRef The actor reference to wrap
     */
    public SpringActorHandle(Scheduler scheduler, ActorRef<T> actorRef) {
        this(scheduler, actorRef, ActorConstants.DEFAULT_TIMEOUT);
    }

    /**
     * Creates a new SpringActorHandle with the given scheduler, actor reference, and default timeout.
     *
     * @param scheduler The scheduler to use for asking messages
     * @param actorRef The actor reference to wrap
     * @param defaultTimeout The default timeout for ask operations
     */
    public SpringActorHandle(Scheduler scheduler, ActorRef<T> actorRef, Duration defaultTimeout) {
        if (scheduler == null) {
            throw new IllegalArgumentException("scheduler must not be null");
        }
        if (actorRef == null) {
            throw new IllegalArgumentException("actorRef must not be null");
        }
        if (defaultTimeout == null) {
            throw new IllegalArgumentException("defaultTimeout must not be null");
        }
        this.scheduler = scheduler;
        this.actorRef = actorRef;
        this.defaultTimeout = defaultTimeout;
    }

    /**
     * Asks the actor a question using an AskCommand and returns a builder for configuring
     * the ask operation. This method automatically injects the reply-to reference into the command.
     *
     * <p>Example usage:
     * <pre>
     * {@code
     * // Simple ask
     * CompletionStage<String> result = springActorHandle.ask(new GetUserName("user123")).execute();
     *
     * // With timeout
     * CompletionStage<String> result = springActorHandle.ask(new GetUserName("user123"))
     *     .withTimeout(Duration.ofSeconds(5))
     *     .execute();
     *
     * // With timeout handler
     * CompletionStage<String> result = springActorHandle.ask(new GetUserName("user123"))
     *     .withTimeout(Duration.ofSeconds(5))
     *     .onTimeout(() -> "default-value")
     *     .execute();
     * }
     * </pre>
     *
     * @param command The command that implements AskCommand (must also be assignable to T)
     * @param <RES> The type of the response message
     * @return An AskBuilder for configuring and executing the ask operation
     */
    @SuppressWarnings("unchecked")
    public <RES> AskBuilder<T, RES> ask(AskCommand<RES> command) {
        return new AskBuilder<>(replyTo -> (T) command.withReplyTo(replyTo), actorRef, scheduler, defaultTimeout);
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
     *
     * <p><b>Warning:</b> This method uses PoisonPill which is not type-safe. The actor's
     * Command interface must be able to accept Object messages for this to work. If your
     * actor uses a sealed command hierarchy, consider implementing a Stop command instead.
     */
    @SuppressWarnings("unchecked")
    public void stop() {
        actorRef.tell((T) PoisonPill.getInstance());
    }

    /**
     * Returns the path of this actor in the actor system hierarchy.
     * The path uniquely identifies the actor's location in the system.
     *
     * <p>This is useful for debugging, logging, and understanding actor hierarchies.
     *
     * @return The actor's path as a string
     */
    public String getPath() {
        return actorRef.path().toString();
    }

    /**
     * Creates a unified reference to a specific child actor for performing operations.
     * This is the recommended way to interact with child actors as it provides a concise,
     * single-entry-point API for all child operations.
     *
     * <p>Example usage:
     * <pre>
     * {@code
     * // Get existing child
     * Optional<SpringActorHandle<ChildCommand>> child = parentRef
     *     .child(ChildActor.class, "child-1")
     *     .get()
     *     .toCompletableFuture()
     *     .get();
     *
     * // Check existence
     * boolean exists = parentRef
     *     .child(ChildActor.class, "child-1")
     *     .exists()
     *     .toCompletableFuture()
     *     .get();
     *
     * // Spawn new child
     * SpringActorHandle<ChildCommand> child = parentRef
     *     .child(ChildActor.class, "child-1")
     *     .spawn(SupervisorStrategy.restart())
     *     .toCompletableFuture()
     *     .get();
     *
     * // Get or spawn (convenience)
     * SpringActorHandle<ChildCommand> child = parentRef
     *     .child(ChildActor.class, "child-1")
     *     .getOrSpawn(SupervisorStrategy.restart())
     *     .toCompletableFuture()
     *     .get();
     * }
     * </pre>
     *
     * <p><b>Important:</b> The parent actor must have framework commands enabled
     * (automatically enabled if Command extends FrameworkCommand) for child operations to work.
     *
     * @param childActorClass The child actor class (must be a Spring component)
     * @param childId The unique ID for the child actor
     * @param <CC> The command type of the child actor
     * @return A unified reference for performing operations on the child actor
     */
    public <CC> SpringChildActorReference<T, CC> child(
            Class<? extends SpringActorWithContext<CC, ?>> childActorClass, String childId) {
        return new SpringChildActorReference<>(actorRef, scheduler, childActorClass, childId, defaultTimeout);
    }

    /**
     * Creates a fluent builder for spawning a child actor with advanced configuration options.
     *
     * <p>This method is useful when you need to configure the child actor with custom context
     * or other advanced options before spawning. For simple use cases, prefer
     * {@link #child(Class, String)} which provides a more concise API.
     *
     * <p>Example usage:
     * <pre>
     * {@code
     * SpringActorHandle<ChildCommand> child = parentRef
     *     .child(ChildActor.class)
     *     .withId("child-1")
     *     .withSupervisionStrategy(SupervisorStrategy.restart())
     *     .withTimeout(Duration.ofSeconds(5))
     *     .spawn()
     *     .toCompletableFuture()
     *     .get();
     * }
     * </pre>
     *
     * <p><b>Important:</b> The parent actor must have framework commands enabled
     * (automatically enabled if Command extends FrameworkCommand) for child spawning to work.
     *
     * @param childActorClass The child actor class (must be a Spring component)
     * @param <CC> The command type of the child actor
     * @return A fluent builder for configuring and spawning the child actor
     */
    public <CC> SpringChildActorBuilder<T, CC> child(Class<? extends SpringActorWithContext<CC, ?>> childActorClass) {
        return new SpringChildActorBuilder<>(actorRef, scheduler, childActorClass, defaultTimeout);
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

        @Nullable private Supplier<RES> timeoutHandler;

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
            if (timeout == null) {
                throw new IllegalArgumentException("timeout must not be null");
            }
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
                final Supplier<RES> handler = timeoutHandler;
                result = result.exceptionally(throwable -> {
                    if (throwable instanceof TimeoutException
                            || (throwable.getCause() != null && throwable.getCause() instanceof TimeoutException)) {
                        return handler.get();
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

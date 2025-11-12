package io.github.seonwkim.core.shard;

import java.time.Duration;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
import javax.annotation.Nullable;

import io.github.seonwkim.core.AskCommand;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Scheduler;
import org.apache.pekko.actor.typed.javadsl.AskPattern;
import org.apache.pekko.cluster.sharding.typed.javadsl.EntityRef;
import org.apache.pekko.japi.function.Function;

/**
 * A wrapper around Pekko's EntityRef that provides methods for asking and telling messages to a
 * sharded actor. This class simplifies interaction with sharded actors by providing a more
 * Spring-friendly API.
 *
 * @param <T> The type of messages that the actor can handle
 */
public class SpringShardedActorRef<T> {

    private final Scheduler scheduler;
    private final EntityRef<T> entityRef;
    private final Duration defaultTimeout;

    /**
     * Creates a builder for SpringShardedActorRef.
     *
     * @param scheduler The scheduler to use for asking messages
     * @param entityRef The entity reference to wrap
     * @param <T> The type of messages that the actor can handle
     * @return A new builder for SpringShardedActorRef
     */
    public static <T> SpringShardedActorRefBuilder<T> builder(Scheduler scheduler, EntityRef<T> entityRef) {
        return new SpringShardedActorRefBuilder<>(scheduler, entityRef);
    }

    /** Default value for the default timeout in seconds. */
    public static final int DEFAULT_TIMEOUT_SECONDS = 3;

    /**
     * Creates a new SpringShardedActorRef with the given scheduler and entity reference. Uses the
     * default timeout of 3 seconds.
     *
     * @param scheduler The scheduler to use for asking messages
     * @param entityRef The entity reference to wrap
     */
    public SpringShardedActorRef(Scheduler scheduler, EntityRef<T> entityRef) {
        this(scheduler, entityRef, Duration.ofSeconds(DEFAULT_TIMEOUT_SECONDS));
    }

    /**
     * Creates a new SpringShardedActorRef with the given scheduler, entity reference, and default
     * timeout.
     *
     * @param scheduler The scheduler to use for asking messages
     * @param entityRef The entity reference to wrap
     * @param defaultTimeout The default timeout for ask operations
     */
    public SpringShardedActorRef(Scheduler scheduler, EntityRef<T> entityRef, Duration defaultTimeout) {
        if (scheduler == null) {
            throw new IllegalArgumentException("scheduler must not be null");
        }
        if (entityRef == null) {
            throw new IllegalArgumentException("entityRef must not be null");
        }
        if (defaultTimeout == null) {
            throw new IllegalArgumentException("defaultTimeout must not be null");
        }
        this.scheduler = scheduler;
        this.entityRef = entityRef;
        this.defaultTimeout = defaultTimeout;
    }

    /**
     * Asks the sharded actor a question using an AskCommand and returns a builder for configuring
     * the ask operation. This method automatically injects the reply-to reference into the command.
     *
     * <p>Example usage:
     * <pre>
     * {@code
     * // Simple ask
     * CompletionStage<String> result = shardedActorRef.ask(new GetUserName("user123")).execute();
     *
     * // With timeout
     * CompletionStage<String> result = shardedActorRef.ask(new GetUserName("user123"))
     *     .withTimeout(Duration.ofSeconds(5))
     *     .execute();
     *
     * // With timeout handler
     * CompletionStage<String> result = shardedActorRef.ask(new GetUserName("user123"))
     *     .withTimeout(Duration.ofSeconds(5))
     *     .onTimeout(() -> "default-value")
     *     .execute();
     * }</pre>
     *
     * @param command The command that implements AskCommand (must also be assignable to T)
     * @param <RES> The type of the response message
     * @return An AskBuilder for configuring and executing the ask operation
     */
    @SuppressWarnings("unchecked")
    public <RES> AskBuilder<T, RES> ask(AskCommand<RES> command) {
        return new AskBuilder<>(replyTo -> (T) command.withReplyTo(replyTo), entityRef, scheduler, defaultTimeout);
    }

    /**
     * Sends a message to the sharded actor without expecting a response.
     *
     * @param message The message to send
     */
    public void tell(T message) {
        entityRef.tell(message);
    }

    /**
     * Returns the underlying entity reference.
     *
     * @return The underlying Pekko EntityRef
     */
    public EntityRef<T> getUnderlying() {
        return entityRef;
    }

    /**
     * Fluent builder for asking the sharded actor a question with advanced configuration options.
     * This builder provides a more flexible API compared to the simple ask() methods,
     * allowing configuration of timeouts and error handling.
     *
     * @param <REQ> The type of the request message
     * @param <RES> The type of the response message
     */
    public static class AskBuilder<REQ, RES> {
        private final Function<ActorRef<RES>, REQ> messageFactory;
        private final EntityRef<REQ> entityRef;
        private final Scheduler scheduler;
        private Duration timeout;

        @Nullable private Supplier<RES> timeoutHandler;

        /**
         * Creates a new AskBuilder.
         *
         * @param messageFactory The message factory function
         * @param entityRef The entity reference to ask
         * @param scheduler The scheduler for the ask pattern
         * @param defaultTimeout The default timeout
         */
        AskBuilder(
                Function<ActorRef<RES>, REQ> messageFactory,
                EntityRef<REQ> entityRef,
                Scheduler scheduler,
                Duration defaultTimeout) {
            this.messageFactory = messageFactory;
            this.entityRef = entityRef;
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
            CompletionStage<RES> result = AskPattern.ask(entityRef, messageFactory::apply, timeout, scheduler);

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

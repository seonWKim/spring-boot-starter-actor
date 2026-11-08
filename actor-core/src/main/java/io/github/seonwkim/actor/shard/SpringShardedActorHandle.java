package io.github.seonwkim.actor.shard;

import io.github.seonwkim.actor.ActorConstants;
import io.github.seonwkim.actor.AskCommand;
import io.github.seonwkim.actor.SpringActorHandle;
import io.github.seonwkim.actor.internal.PekkoActorRef;
import io.github.seonwkim.actor.internal.PekkoAskBuilder;
import java.time.Duration;
import org.apache.pekko.actor.typed.Scheduler;
import org.apache.pekko.cluster.sharding.typed.javadsl.EntityRef;

/**
 * A wrapper around Pekko's EntityRef that provides methods for asking and telling messages to a
 * sharded actor. This class simplifies interaction with sharded actors by providing a more
 * Spring-friendly API.
 *
 * @param <T> The type of messages that the actor can handle
 */
public class SpringShardedActorHandle<T> {

    private final Scheduler scheduler;
    private final EntityRef<T> entityRef;
    private final Duration defaultTimeout;

    /**
     * Creates a builder for SpringShardedActorHandle.
     *
     * @param scheduler The scheduler to use for asking messages
     * @param entityRef The entity reference to wrap
     * @param <T> The type of messages that the actor can handle
     * @return A new builder for SpringShardedActorHandle
     */
    public static <T> SpringShardedActorHandleBuilder<T> builder(Scheduler scheduler, EntityRef<T> entityRef) {
        return new SpringShardedActorHandleBuilder<>(scheduler, entityRef);
    }

    /** Default value for the default timeout in seconds. */
    public static final int DEFAULT_TIMEOUT_SECONDS = ActorConstants.DEFAULT_TIMEOUT_SECONDS;

    /**
     * Creates a new SpringShardedActorHandle with the given scheduler and entity reference. Uses the
     * default timeout of 3 seconds.
     *
     * @param scheduler The scheduler to use for asking messages
     * @param entityRef The entity reference to wrap
     */
    public SpringShardedActorHandle(Scheduler scheduler, EntityRef<T> entityRef) {
        this(scheduler, entityRef, ActorConstants.DEFAULT_TIMEOUT);
    }

    /**
     * Creates a new SpringShardedActorHandle with the given scheduler, entity reference, and default
     * timeout.
     *
     * @param scheduler The scheduler to use for asking messages
     * @param entityRef The entity reference to wrap
     * @param defaultTimeout The default timeout for ask operations
     */
    public SpringShardedActorHandle(Scheduler scheduler, EntityRef<T> entityRef, Duration defaultTimeout) {
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
    public <RES> SpringActorHandle.AskBuilder<RES> ask(AskCommand<RES> command) {
        return new PekkoAskBuilder<T, RES>(
                entityRef, scheduler, defaultTimeout, replyTo -> (T) command.withReplyTo(new PekkoActorRef<>(replyTo)));
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
}

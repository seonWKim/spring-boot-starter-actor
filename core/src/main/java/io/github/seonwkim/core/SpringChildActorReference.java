package io.github.seonwkim.core;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletionStage;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Scheduler;
import org.apache.pekko.actor.typed.javadsl.AskPattern;

/**
 * A reference to a child actor that provides operations for querying existing child actors.
 * This class is for pure referencing operations only - to spawn new child actors, use
 * {@link SpringActorRef#child(Class)} which returns a {@link SpringChildActorBuilder}.
 *
 * <p>This class eliminates redundant class and ID parameters by capturing them once and providing
 * simple methods for checking actor existence and getting references.
 *
 * <p>Example usage:
 * <pre>
 * {@code
 * // Get existing child
 * Optional<SpringActorRef<ChildCommand>> child = parentRef
 *     .child(ChildActor.class, "child-1")
 *     .get()
 *     .toCompletableFuture()
 *     .get();
 *
 * // Check if child exists
 * boolean exists = parentRef
 *     .child(ChildActor.class, "child-1")
 *     .exists()
 *     .toCompletableFuture()
 *     .get();
 * }
 * </pre>
 *
 * <p><b>Important:</b> The parent actor must have framework commands enabled
 * (automatically enabled if Command extends FrameworkCommand) for child operations to work.
 *
 * @param <P> The parent actor's command type
 * @param <C> The child actor's command type
 * @see SpringChildActorBuilder for spawning new child actors
 */
public class SpringChildActorReference<P, C> {
    private final ActorRef<P> parentRef;
    private final Scheduler scheduler;
    private final Class<? extends SpringActorWithContext<C, ?>> childActorClass;
    private final String childId;
    private final Duration defaultTimeout;
    private Duration timeout;

    /**
     * Creates a new SpringChildActorReference.
     *
     * @param parentRef The parent actor reference
     * @param scheduler The scheduler for ask operations
     * @param childActorClass The child actor class
     * @param childId The unique ID of the child actor
     * @param defaultTimeout The default timeout for operations
     */
    public SpringChildActorReference(
            ActorRef<P> parentRef,
            Scheduler scheduler,
            Class<? extends SpringActorWithContext<C, ?>> childActorClass,
            String childId,
            Duration defaultTimeout) {
        if (parentRef == null) {
            throw new IllegalArgumentException("parentRef must not be null");
        }
        if (scheduler == null) {
            throw new IllegalArgumentException("scheduler must not be null");
        }
        if (childActorClass == null) {
            throw new IllegalArgumentException("childActorClass must not be null");
        }
        if (childId == null || childId.trim().isEmpty()) {
            throw new IllegalArgumentException("childId must not be null or empty");
        }
        this.parentRef = parentRef;
        this.scheduler = scheduler;
        this.childActorClass = childActorClass;
        this.childId = childId;
        this.defaultTimeout = defaultTimeout;
        this.timeout = defaultTimeout;
    }

    /**
     * Sets the timeout for subsequent operations.
     * This affects get() and exists() calls made after this method.
     *
     * @param timeout The timeout duration
     * @return This reference for method chaining
     */
    public SpringChildActorReference<P, C> withTimeout(Duration timeout) {
        if (timeout == null) {
            throw new IllegalArgumentException("timeout must not be null");
        }
        this.timeout = timeout;
        return this;
    }

    /**
     * Gets a reference to the child actor if it exists.
     * Returns an empty Optional if the child doesn't exist.
     *
     * <p>This method is non-blocking and returns a CompletionStage that will be completed
     * with the result of the operation.
     *
     * @return A CompletionStage that will be completed with an Optional containing the child
     *         actor reference if found, or an empty Optional if not found
     */
    @SuppressWarnings("unchecked")
    public CompletionStage<Optional<SpringActorRef<C>>> get() {
        ActorRef<Object> parentAsObject = (ActorRef<Object>) parentRef;

        return AskPattern.ask(
                        parentAsObject,
                        (ActorRef<FrameworkCommands.GetChildResponse<C>> replyTo) ->
                                new FrameworkCommands.GetChild<>(childActorClass, childId, replyTo),
                        timeout,
                        scheduler)
                .thenApply(response -> {
                    if (response.found && response.childRef != null) {
                        return Optional.of(new SpringActorRef<>(scheduler, response.childRef, defaultTimeout));
                    } else {
                        return Optional.empty();
                    }
                });
    }

    /**
     * Checks if the child actor exists.
     *
     * <p>This method is non-blocking and returns a CompletionStage that will be completed
     * with the result of the operation.
     *
     * @return A CompletionStage that will be completed with true if the child exists,
     *         false otherwise
     */
    @SuppressWarnings("unchecked")
    public CompletionStage<Boolean> exists() {
        ActorRef<Object> parentAsObject = (ActorRef<Object>) parentRef;

        return AskPattern.ask(
                        parentAsObject,
                        (ActorRef<FrameworkCommands.ExistsChildResponse> replyTo) ->
                                new FrameworkCommands.ExistsChild<>(childActorClass, childId, replyTo),
                        timeout,
                        scheduler)
                .thenApply(response -> response.exists);
    }
}

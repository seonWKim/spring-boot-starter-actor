package io.github.seonwkim.core;

import io.github.seonwkim.core.impl.DefaultSpringActorContext;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Scheduler;
import org.apache.pekko.actor.typed.SupervisorStrategy;
import org.apache.pekko.actor.typed.javadsl.AskPattern;

/**
 * A unified reference to a child actor that provides a fluent API for all child actor operations.
 * This class eliminates redundant class and ID parameters by capturing them once and providing
 * simple methods for common operations.
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
 *
 * // Spawn new child (fails if exists)
 * SpringActorRef<ChildCommand> child = parentRef
 *     .child(ChildActor.class, "child-1")
 *     .spawn(SupervisorStrategy.restart())
 *     .toCompletableFuture()
 *     .get();
 *
 * // Get or spawn (convenience method)
 * SpringActorRef<ChildCommand> child = parentRef
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
 * @param <P> The parent actor's command type
 * @param <C> The child actor's command type
 */
public class SpringChildActorReference<P, C> {
    private final ActorRef<P> parentRef;
    private final Scheduler scheduler;
    private final Class<? extends SpringActorWithContext<C, ?>> childActorClass;
    private final String childId;
    private final Duration defaultTimeout;
    private Duration timeout;
    private MailboxConfig mailboxConfig;
    private DispatcherConfig dispatcherConfig;
    private TagsConfig tagsConfig;

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
        this.mailboxConfig = MailboxConfig.defaultMailbox();
        this.dispatcherConfig = DispatcherConfig.defaultDispatcher();
        this.tagsConfig = TagsConfig.empty();
    }

    /**
     * Sets the timeout for subsequent operations.
     * This affects get(), exists(), spawn(), and getOrSpawn() calls made after this method.
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
     * Sets the mailbox configuration for the child actor.
     * This affects spawn() and getOrSpawn() calls made after this method.
     *
     * @param mailboxConfig The mailbox configuration
     * @return This reference for method chaining
     */
    public SpringChildActorReference<P, C> withMailbox(MailboxConfig mailboxConfig) {
        if (mailboxConfig == null) {
            throw new IllegalArgumentException("mailboxConfig must not be null");
        }
        this.mailboxConfig = mailboxConfig;
        return this;
    }

    /**
     * Sets the dispatcher configuration for the child actor.
     * This affects spawn() and getOrSpawn() calls made after this method.
     *
     * @param dispatcherConfig The dispatcher configuration
     * @return This reference for method chaining
     */
    public SpringChildActorReference<P, C> withDispatcher(DispatcherConfig dispatcherConfig) {
        if (dispatcherConfig == null) {
            throw new IllegalArgumentException("dispatcherConfig must not be null");
        }
        this.dispatcherConfig = dispatcherConfig;
        return this;
    }

    /**
     * Sets the tags configuration for the child actor.
     * This affects spawn() and getOrSpawn() calls made after this method.
     *
     * @param tagsConfig The tags configuration
     * @return This reference for method chaining
     */
    public SpringChildActorReference<P, C> withTags(TagsConfig tagsConfig) {
        if (tagsConfig == null) {
            throw new IllegalArgumentException("tagsConfig must not be null");
        }
        this.tagsConfig = tagsConfig;
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

    /**
     * Spawns a new child actor with the default supervision strategy (stop on failure).
     * This method fails if a child with the same ID already exists.
     *
     * <p>This method is non-blocking and returns a CompletionStage that will be completed
     * with the child actor reference, or fail with a RuntimeException if the child already exists.
     *
     * <p>If you want to get an existing child or spawn it if it doesn't exist,
     * use {@link #getOrSpawn()} instead.
     *
     * @return A CompletionStage that will be completed with the child actor reference
     * @throws RuntimeException If the child already exists or spawning fails
     */
    public CompletionStage<SpringActorRef<C>> spawn() {
        return spawn(SupervisorStrategy.stop());
    }

    /**
     * Spawns a new child actor with the specified supervision strategy.
     * If a child with the same ID already exists, returns the existing child.
     *
     * <p>This method is non-blocking and returns a CompletionStage that will be completed
     * with the child actor reference. This behavior matches the existing SpringChildActorBuilder
     * for backward compatibility.
     *
     * <p>If you want to explicitly check whether the child was newly created or already existed,
     * use {@link #exists()} before calling this method, or use {@link #getOrSpawn(SupervisorStrategy)}
     * which has the same idempotent behavior.
     *
     * @param strategy The supervision strategy for the child actor
     * @return A CompletionStage that will be completed with the child actor reference
     * @throws RuntimeException If spawning fails for reasons other than the child already existing
     */
    @SuppressWarnings("unchecked")
    public CompletionStage<SpringActorRef<C>> spawn(SupervisorStrategy strategy) {
        if (strategy == null) {
            throw new IllegalArgumentException("strategy must not be null");
        }

        SpringActorContext childContext = new DefaultSpringActorContext(childId);
        ActorRef<Object> parentAsObject = (ActorRef<Object>) parentRef;

        return AskPattern.ask(
                        parentAsObject,
                        (ActorRef<FrameworkCommands.SpawnChildResponse<C>> replyTo) ->
                                new FrameworkCommands.SpawnChild<>(childActorClass, childContext, strategy, mailboxConfig, dispatcherConfig, tagsConfig, replyTo),
                        timeout,
                        scheduler)
                .thenApply(response -> {
                    if ((response.success || "Child already exists".equals(response.message)) && response.childRef != null) {
                        return new SpringActorRef<>(scheduler, response.childRef, defaultTimeout);
                    } else {
                        throw new RuntimeException("Failed to spawn child: " + response.message);
                    }
                });
    }

    /**
     * Gets a reference to the child actor if it exists, or spawns it with the default
     * supervision strategy (stop on failure) if it doesn't exist.
     *
     * <p>This is a convenience method that combines {@link #get()} and {@link #spawn()}.
     * It's useful when you want to ensure a child exists without caring whether it was
     * just created or already existed.
     *
     * <p>This method is non-blocking and returns a CompletionStage that will be completed
     * with the child actor reference.
     *
     * @return A CompletionStage that will be completed with the child actor reference
     */
    public CompletionStage<SpringActorRef<C>> getOrSpawn() {
        return getOrSpawn(SupervisorStrategy.stop());
    }

    /**
     * Gets a reference to the child actor if it exists, or spawns it with the specified
     * supervision strategy if it doesn't exist.
     *
     * <p>This is a convenience method that combines {@link #get()} and {@link #spawn(SupervisorStrategy)}.
     * It's useful when you want to ensure a child exists without caring whether it was
     * just created or already existed.
     *
     * <p>This method is non-blocking and returns a CompletionStage that will be completed
     * with the child actor reference.
     *
     * @param strategy The supervision strategy to use if spawning is needed
     * @return A CompletionStage that will be completed with the child actor reference
     */
    @SuppressWarnings("unchecked")
    public CompletionStage<SpringActorRef<C>> getOrSpawn(SupervisorStrategy strategy) {
        if (strategy == null) {
            throw new IllegalArgumentException("strategy must not be null");
        }

        ActorRef<Object> parentAsObject = (ActorRef<Object>) parentRef;

        // Try to get existing child first
        return AskPattern.ask(
                        parentAsObject,
                        (ActorRef<FrameworkCommands.GetChildResponse<C>> replyTo) ->
                                new FrameworkCommands.GetChild<>(childActorClass, childId, replyTo),
                        timeout,
                        scheduler)
                .thenCompose(response -> {
                    if (response.found && response.childRef != null) {
                        // Child exists, return it
                        return CompletableFuture.completedFuture(
                                new SpringActorRef<>(scheduler, response.childRef, defaultTimeout));
                    } else {
                        // Child doesn't exist, spawn it
                        return spawn(strategy);
                    }
                });
    }
}

package io.github.seonwkim.core;

import io.github.seonwkim.core.impl.DefaultSpringActorContext;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Scheduler;
import org.apache.pekko.actor.typed.SupervisorStrategy;
import org.apache.pekko.actor.typed.javadsl.AskPattern;
import org.springframework.util.ObjectUtils;

import javax.annotation.Nullable;

/**
 * A fluent builder for spawning child actors with a consistent API.
 * This builder provides the same fluent interface as SpringActorSpawnBuilder
 * but for child actors spawned from a parent actor.
 *
 * <p>Example usage:
 * <pre>
 * SpringActorRef&lt;ChildCommand&gt; child = parentRef
 *     .child(ChildActor.class)
 *     .withId("child-1")
 *     .withSupervisionStrategy(SupervisorStrategy.restart())
 *     .withTimeout(Duration.ofSeconds(5))
 *     .spawn()
 *     .toCompletableFuture()
 *     .get();
 * </pre>
 *
 * @param <P> The parent actor's command type
 * @param <C> The child actor's command type
 */
public class SpringChildActorBuilder<P, C> {
    private final ActorRef<P> parentRef;
    private final Scheduler scheduler;
    private final Class<? extends SpringActorWithContext<C, ?>> childActorClass;
    private final Duration defaultTimeout;

    @Nullable private String childId;
    @Nullable private SpringActorContext childContext;
    @Nullable private SupervisorStrategy supervisionStrategy;
    @Nullable private MailboxConfig mailboxConfig;
    private Duration timeout;

    /**
     * Creates a new SpringChildActorBuilder.
     *
     * @param parentRef The parent actor reference
     * @param scheduler The scheduler for ask operations
     * @param childActorClass The child actor class
     * @param defaultTimeout The default timeout for operations
     */
    public SpringChildActorBuilder(
            ActorRef<P> parentRef,
            Scheduler scheduler,
            Class<? extends SpringActorWithContext<C, ?>> childActorClass,
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
        this.parentRef = parentRef;
        this.scheduler = scheduler;
        this.childActorClass = childActorClass;
        this.defaultTimeout = defaultTimeout;
        this.timeout = defaultTimeout;
    }

    /**
     * Sets the ID for the child actor.
     *
     * @param childId The child actor ID
     * @return This builder for method chaining
     */
    public SpringChildActorBuilder<P, C> withId(String childId) {
        if (childId == null || ObjectUtils.isEmpty(childId)) {
            throw new IllegalArgumentException("childId must not be null or empty");
        }
        this.childId = childId;
        return this;
    }

    /**
     * Sets a custom context for the child actor.
     *
     * @param context The custom child actor context
     * @return This builder for method chaining
     */
    public SpringChildActorBuilder<P, C> withContext(SpringActorContext context) {
        if (context == null) {
            throw new IllegalArgumentException("context must not be null");
        }
        this.childContext = context;
        return this;
    }

    /**
     * Sets the supervision strategy for the child actor.
     *
     * @param strategy The supervision strategy
     * @return This builder for method chaining
     */
    public SpringChildActorBuilder<P, C> withSupervisionStrategy(SupervisorStrategy strategy) {
        this.supervisionStrategy = strategy;
        return this;
    }

    /**
     * Sets the timeout for the spawn operation.
     *
     * @param timeout The timeout duration
     * @return This builder for method chaining
     */
    public SpringChildActorBuilder<P, C> withTimeout(Duration timeout) {
        if (timeout == null) {
            throw new IllegalArgumentException("timeout must not be null");
        }
        this.timeout = timeout;
        return this;
    }

    /**
     * Sets the mailbox configuration using the type-safe MailboxConfig API.
     *
     * <p>Example usage:
     * <pre>{@code
     * parent.child(ChildActor.class)
     *     .withId("worker")
     *     .withMailbox(MailboxConfig.bounded(100))
     *     .spawn();
     * }</pre>
     *
     * @param mailboxConfig The mailbox configuration
     * @return This builder for method chaining
     */
    public SpringChildActorBuilder<P, C> withMailbox(MailboxConfig mailboxConfig) {
        if (mailboxConfig == null) {
            throw new IllegalArgumentException("mailboxConfig must not be null");
        }
        this.mailboxConfig = mailboxConfig;
        return this;
    }

    /**
     * Spawns the child actor and returns a CompletionStage with the child actor reference.
     * If the child already exists, the existing reference is returned.
     *
     * @return A CompletionStage that will be completed with the child actor reference
     * @throws IllegalStateException If neither childId nor childContext is set
     */
    @SuppressWarnings("unchecked")
    public CompletionStage<SpringActorRef<C>> spawn() {
        if (childContext == null) {
            if (childId == null) {
                throw new IllegalStateException("Either childId or childContext must be set");
            }
            childContext = new DefaultSpringActorContext(childId);
        }

        // Cast parent ref to Object to send framework command
        ActorRef<Object> parentAsObject = (ActorRef<Object>) parentRef;

        final SpringActorContext context = childContext;
        final SupervisorStrategy strategy = supervisionStrategy;
        final MailboxConfig mailbox = mailboxConfig;

        return AskPattern.ask(
                        parentAsObject,
                        (ActorRef<FrameworkCommands.SpawnChildResponse<C>> replyTo) ->
                                new FrameworkCommands.SpawnChild<>(
                                        childActorClass, context, strategy, mailbox, replyTo),
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
     * Spawns the child actor synchronously and returns the child actor reference.
     * This method blocks until the child is spawned.
     *
     * @return The child actor reference
     * @throws IllegalStateException If neither childId nor childContext is set
     */
    public SpringActorRef<C> spawnAndWait() {
        return spawn().toCompletableFuture().join();
    }

    /**
     * Gets a reference to the child actor if it exists, or spawns it if it doesn't.
     * This is a convenience method that combines exists checking and spawning.
     *
     * @return A CompletionStage that will be completed with the child actor reference
     * @throws IllegalStateException If neither childId nor childContext is set
     */
    @SuppressWarnings("unchecked")
    public CompletionStage<SpringActorRef<C>> getOrSpawn() {
        if (childContext == null) {
            if (childId == null) {
                throw new IllegalStateException("Either childId or childContext must be set");
            }
            childContext = new DefaultSpringActorContext(childId);
        }

        // Ensure childContext is initialized
        if (childContext == null) {
            throw new IllegalStateException("childContext must be initialized");
        }

        final SpringActorContext context = childContext;

        // Try to get existing child first
        ActorRef<Object> parentAsObject = (ActorRef<Object>) parentRef;

        return AskPattern.ask(
                        parentAsObject,
                        (ActorRef<FrameworkCommands.GetChildResponse<C>> replyTo) ->
                                new FrameworkCommands.GetChild<>(childActorClass, context.actorId(), replyTo),
                        timeout,
                        scheduler)
                .thenCompose(response -> {
                    if (response.found && response.childRef != null) {
                        // Child exists, return it
                        return CompletableFuture.completedFuture(
                                new SpringActorRef<>(scheduler, response.childRef, defaultTimeout));
                    } else {
                        // Child doesn't exist, spawn it
                        return spawn();
                    }
                });
    }

    /**
     * Gets a reference to the child actor if it exists.
     * Returns null if the child doesn't exist.
     *
     * @return A CompletionStage that will be completed with the child actor reference, or null if not found
     * @throws IllegalStateException If neither childId nor childContext is set
     */
    @SuppressWarnings("unchecked")
    public CompletionStage<SpringActorRef<C>> get() {
        if (childContext == null) {
            if (childId == null) {
                throw new IllegalStateException("Either childId or childContext must be set");
            }
            childContext = new DefaultSpringActorContext(childId);
        }

        // Ensure childContext is initialized
        if (childContext == null) {
            throw new IllegalStateException("childContext must be initialized");
        }

        final SpringActorContext context = childContext;
        ActorRef<Object> parentAsObject = (ActorRef<Object>) parentRef;

        return AskPattern.ask(
                        parentAsObject,
                        (ActorRef<FrameworkCommands.GetChildResponse<C>> replyTo) ->
                                new FrameworkCommands.GetChild<>(childActorClass, context.actorId(), replyTo),
                        timeout,
                        scheduler)
                .thenApply(response -> {
                    if (response.found && response.childRef != null) {
                        return new SpringActorRef<>(scheduler, response.childRef, defaultTimeout);
                    } else {
                        return null;
                    }
                });
    }

    /**
     * Checks if the child actor exists.
     *
     * @return A CompletionStage that will be completed with true if the child exists, false otherwise
     * @throws IllegalStateException If neither childId nor childContext is set
     */
    @SuppressWarnings("unchecked")
    public CompletionStage<Boolean> exists() {
        if (childContext == null) {
            if (childId == null) {
                throw new IllegalStateException("Either childId or childContext must be set");
            }
            childContext = new DefaultSpringActorContext(childId);
        }

        // Ensure childContext is initialized
        if (childContext == null) {
            throw new IllegalStateException("childContext must be initialized");
        }

        final SpringActorContext context = childContext;
        ActorRef<Object> parentAsObject = (ActorRef<Object>) parentRef;

        return AskPattern.ask(
                        parentAsObject,
                        (ActorRef<FrameworkCommands.ExistsChildResponse> replyTo) ->
                                new FrameworkCommands.ExistsChild<>(
                                        childActorClass, context.actorId(), replyTo),
                        timeout,
                        scheduler)
                .thenApply(response -> response.exists);
    }
}

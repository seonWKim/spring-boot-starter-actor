package io.github.seonwkim.actor;

import java.time.Duration;
import java.util.concurrent.CompletionStage;

/**
 * A fluent builder for spawning child actors with a consistent API.
 * This builder provides the same fluent interface as SpringActorSpawnBuilder
 * but for child actors spawned from a parent actor.
 *
 * <p>Example usage:
 * <pre>
 * SpringActorHandle&lt;ChildCommand&gt; child = parentRef
 *     .child(ChildActor.class)
 *     .withId("child-1")
 *     .withSupervisionStrategy(SupervisionStrategy.restart())
 *     .withTimeout(Duration.ofSeconds(5))
 *     .spawn()
 *     .toCompletableFuture()
 *     .get();
 * </pre>
 *
 * @param <P> The parent actor's command type
 * @param <C> The child actor's command type
 */
public interface SpringChildActorBuilder<P, C> {

    /**
     * Sets the ID for the child actor.
     *
     * @param childId The child actor ID
     * @return This builder for method chaining
     */
    SpringChildActorBuilder<P, C> withId(String childId);

    /**
     * Sets a custom context for the child actor.
     *
     * @param context The custom child actor context
     * @return This builder for method chaining
     */
    SpringChildActorBuilder<P, C> withContext(SpringActorContext context);

    /**
     * Sets the supervision strategy for the child actor.
     *
     * @param strategy The supervision strategy
     * @return This builder for method chaining
     */
    SpringChildActorBuilder<P, C> withSupervisionStrategy(SupervisionStrategy strategy);

    /**
     * Sets the timeout for the spawn operation.
     *
     * @param timeout The timeout duration
     * @return This builder for method chaining
     */
    SpringChildActorBuilder<P, C> withTimeout(Duration timeout);

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
    SpringChildActorBuilder<P, C> withMailbox(MailboxConfig mailboxConfig);

    /**
     * Sets the dispatcher configuration using the type-safe DispatcherConfig API.
     *
     * <p>Example usage:
     * <pre>{@code
     * parent.child(ChildActor.class)
     *     .withId("worker")
     *     .withDispatcher(DispatcherConfig.blocking())
     *     .spawn();
     * }</pre>
     *
     * @param dispatcherConfig The dispatcher configuration
     * @return This builder for method chaining
     */
    SpringChildActorBuilder<P, C> withDispatcher(DispatcherConfig dispatcherConfig);

    /**
     * Sets the tags configuration for this child actor. Tags are used for logging and categorization,
     * appearing in the MDC pekkoTags attribute.
     *
     * <p>Example usage:
     * <pre>{@code
     * parent.child(ChildActor.class)
     *     .withId("worker")
     *     .withTags(TagsConfig.of("worker", "high-priority"))
     *     .spawn();
     * }</pre>
     *
     * @param tagsConfig The tags configuration
     * @return This builder for method chaining
     */
    SpringChildActorBuilder<P, C> withTags(TagsConfig tagsConfig);

    /**
     * Sets static MDC (Mapped Diagnostic Context) values for this child actor.
     * These values will be included in all log entries from the child actor.
     *
     * <p>MDC is useful for adding contextual information like request IDs, user IDs,
     * correlation IDs, or any other data that should appear in logs.
     *
     * <p>Example usage:
     * <pre>{@code
     * Map<String, String> mdc = Map.of(
     *     "childId", "child-123",
     *     "role", "worker"
     * );
     *
     * parent.child(ChildActor.class)
     *     .withId("worker")
     *     .withMdc(MdcConfig.of(mdc))
     *     .spawn();
     * }</pre>
     *
     * <p>The child actor can combine these static MDC values with dynamic per-message MDC
     * using {@link SpringActorBehavior.Builder#withMdc(java.util.function.Function)}.
     *
     * @param mdcConfig The MDC configuration
     * @return This builder for method chaining
     */
    SpringChildActorBuilder<P, C> withMdc(MdcConfig mdcConfig);

    /**
     * Spawns the child actor and returns a CompletionStage with the child actor reference.
     * If the child already exists, the existing reference is returned.
     *
     * @return A CompletionStage that will be completed with the child actor reference
     * @throws IllegalStateException If neither childId nor childContext is set
     */
    CompletionStage<SpringActorHandle<C>> spawn();

    /**
     * Spawns the child actor synchronously and returns the child actor reference.
     * This method blocks until the child is spawned.
     *
     * @return The child actor reference
     * @throws IllegalStateException If neither childId nor childContext is set
     */
    SpringActorHandle<C> spawnAndWait();

    /**
     * Gets a reference to the child actor if it exists, or spawns it if it doesn't.
     * This is a convenience method that combines exists checking and spawning.
     *
     * @return A CompletionStage that will be completed with the child actor reference
     * @throws IllegalStateException If neither childId nor childContext is set
     */
    CompletionStage<SpringActorHandle<C>> getOrSpawn();

    /**
     * Gets a reference to the child actor if it exists.
     * Returns null if the child doesn't exist.
     *
     * @return A CompletionStage that will be completed with the child actor reference, or null if not found
     * @throws IllegalStateException If neither childId nor childContext is set
     */
    CompletionStage<SpringActorHandle<C>> get();

    /**
     * Checks if the child actor exists.
     *
     * @return A CompletionStage that will be completed with true if the child exists, false otherwise
     * @throws IllegalStateException If neither childId nor childContext is set
     */
    CompletionStage<Boolean> exists();
}

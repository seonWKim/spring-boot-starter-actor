package io.github.seonwkim.actor;

import java.time.Duration;
import java.util.concurrent.CompletionStage;

/**
 * A fluent builder for spawning actors with a simplified API. This builder provides a more
 * convenient way to spawn actors compared to using SpringActorSpawnContext directly.
 *
 * @param <A> The type of the actor
 * @param <C> The type of commands that the actor can handle
 */
public interface SpringActorSpawnBuilder<A extends SpringActorWithContext<C, ?>, C> {

    /**
     * Sets the ID of the actor to spawn.
     *
     * @param actorId The ID of the actor
     * @return This builder
     */
    SpringActorSpawnBuilder<A, C> withId(String actorId);

    /**
     * Sets the timeout for the spawn operation.
     *
     * @param timeout The timeout duration
     * @return This builder
     */
    SpringActorSpawnBuilder<A, C> withTimeout(Duration timeout);

    /**
     * Sets a custom context for the actor.
     *
     * @param context The custom actor context
     * @return This builder
     */
    SpringActorSpawnBuilder<A, C> withContext(SpringActorContext context);

    /**
     * Sets the mailbox configuration using the type-safe MailboxConfig API.
     *
     * <p>Example usage:
     * <pre>{@code
     * // Bounded mailbox
     * .withMailbox(MailboxConfig.bounded(100))
     *
     * // Custom mailbox from configuration
     * .withMailbox(MailboxConfig.fromConfig("my-priority-mailbox"))
     * }</pre>
     *
     * @param mailboxConfig The mailbox configuration
     * @return This builder
     */
    SpringActorSpawnBuilder<A, C> withMailbox(MailboxConfig mailboxConfig);

    /**
     * Sets the dispatcher using a configuration path from application.yml.
     * The dispatcher should be configured under spring.actor in your application.yml.
     *
     * Example configuration in application.yml:
     * <pre>
     * spring:
     *   actor:
     *     my-blocking-dispatcher:
     *       type: Dispatcher
     *       executor: "thread-pool-executor"
     *       thread-pool-executor:
     *         fixed-pool-size: 16
     *       throughput: 1
     * </pre>
     *
     * @param dispatcherPath The path to the dispatcher configuration (e.g., "my-blocking-dispatcher")
     * @return This builder
     */
    SpringActorSpawnBuilder<A, C> withDispatcherFromConfig(String dispatcherPath);

    /**
     * Configures the actor to use the blocking dispatcher.
     * This should be used for actors that perform blocking I/O operations.
     * Uses Pekko's default blocking IO dispatcher.
     *
     * @return This builder
     */
    SpringActorSpawnBuilder<A, C> withBlockingDispatcher();

    /**
     * Configures the actor to use the default dispatcher.
     * This is the default behavior if no dispatcher is specified.
     *
     * @return This builder
     */
    SpringActorSpawnBuilder<A, C> withDefaultDispatcher();

    /**
     * Configures the actor to use the same dispatcher as its parent.
     *
     * @return This builder
     */
    SpringActorSpawnBuilder<A, C> withDispatcherSameAsParent();

    /**
     * Sets the dispatcher from a {@link DispatcherConfig}, the form the child builder also takes.
     *
     * @param dispatcherConfig The dispatcher, e.g. {@code DispatcherConfig.blocking()}
     * @return This builder
     * @since 1.1.0
     */
    default SpringActorSpawnBuilder<A, C> withDispatcher(DispatcherConfig dispatcherConfig) {
        if (dispatcherConfig == null) {
            throw new IllegalArgumentException("dispatcherConfig must not be null");
        }
        return switch (dispatcherConfig) {
            case DispatcherConfig.Default ignored -> withDefaultDispatcher();
            case DispatcherConfig.Blocking ignored -> withBlockingDispatcher();
            case DispatcherConfig.SameAsParent ignored -> withDispatcherSameAsParent();
            case DispatcherConfig.FromConfig fromConfig -> withDispatcherFromConfig(fromConfig.path());
        };
    }

    /**
     * Sets the tags configuration for this actor. Tags are used for logging and categorization,
     * appearing in the MDC pekkoTags attribute.
     *
     * <p>Example usage:
     * <pre>{@code
     * // Single tag
     * .withTags(TagsConfig.of("worker"))
     *
     * // Multiple tags
     * .withTags(TagsConfig.of("worker", "high-priority", "cpu-intensive"))
     * }</pre>
     *
     * @param tagsConfig The tags configuration
     * @return This builder
     */
    SpringActorSpawnBuilder<A, C> withTags(TagsConfig tagsConfig);

    /**
     * Sets static MDC (Mapped Diagnostic Context) values for this actor.
     * These values will be included in all log entries from the actor.
     *
     * <p>MDC is useful for adding contextual information like request IDs, user IDs,
     * correlation IDs, or any other data that should appear in logs.
     *
     * <p>Example usage:
     * <pre>{@code
     * Map<String, String> mdc = Map.of(
     *     "userId", "user-123",
     *     "requestId", "req-456",
     *     "service", "order-service"
     * );
     *
     * .withMdc(MdcConfig.of(mdc))
     * }</pre>
     *
     * <p>The actor can combine these static MDC values with dynamic per-message MDC
     * using {@link SpringActorBehavior.Builder#withMdc(java.util.function.Function)}.
     *
     * @param mdcConfig The MDC configuration
     * @return This builder
     */
    SpringActorSpawnBuilder<A, C> withMdc(MdcConfig mdcConfig);

    /**
     * Sets whether the actor should be a cluster singleton.
     *
     * @param isClusterSingleton Whether the actor should be a cluster singleton
     * @return This builder
     */
    SpringActorSpawnBuilder<A, C> asClusterSingleton(boolean isClusterSingleton);

    /**
     * Marks the actor as a cluster singleton.
     *
     * @return This builder
     */
    SpringActorSpawnBuilder<A, C> asClusterSingleton();

    /**
     * Sets the supervisor strategy for this actor. The supervisor strategy determines how the
     * actor will be supervised by its parent (the root guardian).
     *
     * @param supervisorStrategy The supervisor strategy (e.g., SupervisionStrategy.restart())
     * @return This builder
     */
    SpringActorSpawnBuilder<A, C> withSupervisionStrategy(SupervisionStrategy supervisorStrategy);

    /**
     * Spawns the actor and returns a CompletionStage with the actor reference.
     *
     * @return A CompletionStage that will be completed with a reference to the spawned actor
     * @throws IllegalStateException If neither actorId nor actorContext is set
     */
    CompletionStage<SpringActorHandle<C>> spawn();

    /**
     * Returns the running actor with this builder's id, or spawns it with this builder's settings
     * (dispatcher, mailbox, supervision and the rest) when none runs.
     *
     * @return A CompletionStage that will be completed with a reference to the actor
     * @throws IllegalStateException If neither actorId nor actorContext is set
     */
    CompletionStage<SpringActorHandle<C>> getOrSpawn();

    /**
     * Spawns the actor synchronously and returns the actor reference. This method blocks until the
     * actor is spawned.
     *
     * @return A reference to the spawned actor
     * @throws IllegalStateException If neither actorId nor actorContext is set
     */
    SpringActorHandle<C> spawnAndWait();
}

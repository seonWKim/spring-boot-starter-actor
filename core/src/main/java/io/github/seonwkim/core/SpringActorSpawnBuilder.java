package io.github.seonwkim.core;

import io.github.seonwkim.core.impl.DefaultSpringActorContext;
import java.time.Duration;
import java.util.concurrent.CompletionStage;
import org.apache.pekko.actor.typed.MailboxSelector;
import org.apache.pekko.actor.typed.SupervisorStrategy;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

import javax.annotation.Nullable;

/**
 * A fluent builder for spawning actors with a simplified API. This builder provides a more
 * convenient way to spawn actors compared to using SpringActorSpawnContext directly.
 *
 * @param <A> The type of the actor
 * @param <C> The type of commands that the actor can handle
 */
public class SpringActorSpawnBuilder<A extends SpringActorWithContext<C, ?>, C> {
    private final SpringActorSystem actorSystem;
    private final Class<A> actorClass;
    @Nullable private String actorId;
    @Nullable private SpringActorContext actorContext;
    private Duration timeout = Duration.ofSeconds(3);
    private MailboxConfig mailboxConfig = MailboxConfig.defaultMailbox();
    private DispatcherConfig dispatcherConfig = DispatcherConfig.defaultDispatcher();
    private boolean isClusterSingleton = false;
    @Nullable private SupervisorStrategy supervisorStrategy = null;

    /**
     * Creates a new SpringActorSpawnBuilder.
     *
     * @param actorSystem The actor system to spawn the actor in
     * @param actorClass The class of the actor to spawn
     */
    public SpringActorSpawnBuilder(SpringActorSystem actorSystem, Class<A> actorClass) {
        if (actorSystem == null) {
            throw new IllegalArgumentException("actorSystem must not be null");
        }
        if (actorClass == null) {
            throw new IllegalArgumentException("actorClass must not be null");
        }
        this.actorSystem = actorSystem;
        this.actorClass = actorClass;
    }

    /**
     * Sets the ID of the actor to spawn.
     *
     * @param actorId The ID of the actor
     * @return This builder
     */
    public SpringActorSpawnBuilder<A, C> withId(String actorId) {
        if (actorId == null || ObjectUtils.isEmpty(actorId)) {
            throw new IllegalArgumentException("actorId must not be null or empty");
        }
        this.actorId = actorId;
        return this;
    }

    /**
     * Sets the timeout for the spawn operation.
     *
     * @param timeout The timeout duration
     * @return This builder
     */
    public SpringActorSpawnBuilder<A, C> withTimeout(Duration timeout) {
        if (timeout == null) {
            throw new IllegalArgumentException("timeout must not be null");
        }
        this.timeout = timeout;
        return this;
    }

    /**
     * Sets a custom context for the actor.
     *
     * @param context The custom actor context
     * @return This builder
     */
    public SpringActorSpawnBuilder<A, C> withContext(SpringActorContext context) {
        if (context == null) {
            throw new IllegalArgumentException("context must not be null");
        }
        this.actorContext = context;
        return this;
    }

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
    public SpringActorSpawnBuilder<A, C> withMailbox(MailboxConfig mailboxConfig) {
        if (mailboxConfig == null) {
            throw new IllegalArgumentException("mailboxConfig must not be null");
        }
        this.mailboxConfig = mailboxConfig;
        return this;
    }

    /**
     * Sets the dispatcher using a configuration path from application.yml.
     * The dispatcher should be configured under spring.actor in your application.yml.
     *
     * Example configuration in application.yml:
     * <pre>
     * spring:
     *   actor:
     *     actor:
     *       my-blocking-dispatcher:
     *         type: Dispatcher
     *         executor: "thread-pool-executor"
     *         thread-pool-executor:
     *           fixed-pool-size: 16
     *         throughput: 1
     * </pre>
     *
     * @param dispatcherPath The path to the dispatcher configuration (e.g., "my-blocking-dispatcher")
     * @return This builder
     */
    public SpringActorSpawnBuilder<A, C> withDispatcherFromConfig(String dispatcherPath) {
        this.dispatcherConfig = DispatcherConfig.fromConfig(dispatcherPath);
        return this;
    }

    /**
     * Configures the actor to use the blocking dispatcher.
     * This should be used for actors that perform blocking I/O operations.
     * Uses Pekko's default blocking IO dispatcher.
     *
     * @return This builder
     */
    public SpringActorSpawnBuilder<A, C> withBlockingDispatcher() {
        this.dispatcherConfig = DispatcherConfig.blocking();
        return this;
    }

    /**
     * Configures the actor to use the default dispatcher.
     * This is the default behavior if no dispatcher is specified.
     *
     * @return This builder
     */
    public SpringActorSpawnBuilder<A, C> withDefaultDispatcher() {
        this.dispatcherConfig = DispatcherConfig.defaultDispatcher();
        return this;
    }

    /**
     * Configures the actor to use the same dispatcher as its parent.
     *
     * @return This builder
     */
    public SpringActorSpawnBuilder<A, C> withDispatcherSameAsParent() {
        this.dispatcherConfig = DispatcherConfig.sameAsParent();
        return this;
    }

    /**
     * Sets whether the actor should be a cluster singleton.
     *
     * @param isClusterSingleton Whether the actor should be a cluster singleton
     * @return This builder
     */
    public SpringActorSpawnBuilder<A, C> asClusterSingleton(boolean isClusterSingleton) {
        this.isClusterSingleton = isClusterSingleton;
        return this;
    }

    /**
     * Marks the actor as a cluster singleton.
     *
     * @return This builder
     */
    public SpringActorSpawnBuilder<A, C> asClusterSingleton() {
        return asClusterSingleton(true);
    }

    /**
     * Sets the supervisor strategy for this actor. The supervisor strategy determines how the
     * actor will be supervised by its parent (the root guardian).
     *
     * @param supervisorStrategy The supervisor strategy (e.g., SupervisorStrategy.restart())
     * @return This builder
     */
    public SpringActorSpawnBuilder<A, C> withSupervisonStrategy(SupervisorStrategy supervisorStrategy) {
        this.supervisorStrategy = supervisorStrategy;
        return this;
    }

    /**
     * Spawns the actor and returns a CompletionStage with the actor reference.
     *
     * @return A CompletionStage that will be completed with a reference to the spawned actor
     * @throws IllegalStateException If neither actorId nor actorContext is set
     */
    public CompletionStage<SpringActorRef<C>> spawn() {
        if (actorContext == null) {
            if (actorId == null) {
                throw new IllegalStateException("Either actorId or actorContext must be set");
            }
            actorContext = new DefaultSpringActorContext(actorId);
        }

        return actorSystem.spawn(
                actorClass, actorContext, mailboxConfig, dispatcherConfig, isClusterSingleton, supervisorStrategy, timeout);
    }

    /**
     * Spawns the actor synchronously and returns the actor reference. This method blocks until the
     * actor is spawned.
     *
     * @return A reference to the spawned actor
     * @throws IllegalStateException If neither actorId nor actorContext is set
     */
    public SpringActorRef<C> spawnAndWait() {
        return spawn().toCompletableFuture().join();
    }
}

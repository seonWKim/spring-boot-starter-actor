package io.github.seonwkim.core;

import java.time.Duration;
import java.util.concurrent.CompletionStage;

import org.apache.pekko.actor.typed.MailboxSelector;

import io.github.seonwkim.core.impl.DefaultSpringActorContext;

/**
 * A fluent builder for spawning actors with a simplified API.
 * This builder provides a more convenient way to spawn actors compared to
 * using SpringActorSpawnContext directly.
 *
 * @param <A> The type of the actor
 * @param <C> The type of commands that the actor can handle
 */
public class SpringActorSpawnBuilder<A extends SpringActor<A, C>, C> {
    private final SpringActorSystem actorSystem;
    private final Class<A> actorClass;
    private String actorId;
    private SpringActorContext actorContext;
    private Duration timeout = Duration.ofSeconds(3);
    private MailboxSelector mailboxSelector = MailboxSelector.defaultMailbox();
    private boolean isClusterSingleton = false;

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
        this.actorContext = context;
        return this;
    }

    /**
     * Sets the mailbox selector for the actor.
     *
     * @param mailboxSelector The mailbox selector
     * @return This builder
     */
    public SpringActorSpawnBuilder<A, C> withMailbox(MailboxSelector mailboxSelector) {
        if (mailboxSelector == null) {
            throw new IllegalArgumentException("mailboxSelector must not be null");
        }
        this.mailboxSelector = mailboxSelector;
        return this;
    }

    /**
     * Sets the mailbox selector using a string name.
     * Common values: "default", "bounded", "unbounded"
     *
     * @param mailboxName The name of the mailbox type
     * @return This builder
     */
    public SpringActorSpawnBuilder<A, C> withMailbox(String mailboxName) {
        if ("bounded".equalsIgnoreCase(mailboxName)) {
            return withMailbox(MailboxSelector.bounded(1000));
        } else if ("unbounded".equalsIgnoreCase(mailboxName)) {
            return withMailbox(MailboxSelector.fromConfig("pekko.actor.default-mailbox"));
        } else {
            return withMailbox(MailboxSelector.defaultMailbox());
        }
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
     * Spawns the actor and returns a CompletionStage with the actor reference.
     *
     * @return A CompletionStage that will be completed with a reference to the spawned actor
     * @throws IllegalStateException If neither actorId nor actorContext is set
     */
    public CompletionStage<SpringActorRef<C>> start() {
        if (actorContext == null) {
            if (actorId == null) {
                throw new IllegalStateException("Either actorId or actorContext must be set");
            }
            actorContext = new DefaultSpringActorContext(actorId);
        }

        SpringActorSpawnContext<A, C> spawnContext = new SpringActorSpawnContext<>(
                actorClass,
                actorContext,
                timeout,
                mailboxSelector,
                isClusterSingleton
        );

        return actorSystem.spawn(spawnContext);
    }

    /**
     * Spawns the actor synchronously and returns the actor reference.
     * This method blocks until the actor is spawned.
     *
     * @return A reference to the spawned actor
     * @throws IllegalStateException If neither actorId nor actorContext is set
     */
    public SpringActorRef<C> startAndWait() {
        return start().toCompletableFuture().join();
    }
}

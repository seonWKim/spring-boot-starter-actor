package io.github.seonwkim.core;

import java.time.Duration;

import org.apache.pekko.actor.typed.MailboxSelector;

import io.github.seonwkim.core.impl.DefaultSpringActorContext;

/**
 * Context for spawning an actor. This class encapsulates all the parameters needed to spawn an actor.
 * It is used by the {@link SpringActorSystem#spawn(SpringActorSpawnContext)} method to create a new actor.
 *
 * @param <A> The type of the actor
 * @param <C> The type of commands that the actor can handle
 */
public class SpringActorSpawnContext<A extends SpringActor<A, C>, C> {
    private final Class<A> actorClass;
    private final SpringActorContext actorContext;
    private final Duration timeout;
    private final MailboxSelector mailboxSelector;
    private final boolean isClusterSingleton;

    /**
     * Creates a new SpringActorSpawnContext with the given parameters.
     *
     * @param actorClass The class of the actor to spawn
     * @param actorContext The context of the actor to spawn
     * @param timeout The timeout for the spawn operation
     * @param mailboxSelector The mailbox selector to use for the actor
     * @param isClusterSingleton Whether the actor should be a cluster singleton
     */
    public SpringActorSpawnContext(
            Class<A> actorClass,
            SpringActorContext actorContext,
            Duration timeout,
            MailboxSelector mailboxSelector,
            boolean isClusterSingleton) {
        this.actorClass = actorClass;
        this.actorContext = actorContext;
        this.timeout = timeout;
        this.mailboxSelector = mailboxSelector;
        this.isClusterSingleton = isClusterSingleton;
    }

    /**
     * Returns the class of the actor to spawn.
     *
     * @return The class of the actor to spawn
     */
    public Class<A> getActorClass() {
        return actorClass;
    }

    /**
     * Returns the context of the actor to spawn.
     *
     * @return The context of the actor to spawn
     */
    public SpringActorContext getActorContext() {
        return actorContext;
    }

    /**
     * Returns the timeout for the spawn operation.
     *
     * @return The timeout for the spawn operation
     */
    public Duration getTimeout() {
        return timeout;
    }

    /**
     * Returns the mailbox selector to use for the actor.
     *
     * @return The mailbox selector to use for the actor
     */
    public MailboxSelector getMailboxSelector() {
        return mailboxSelector;
    }

    /**
     * Returns whether the actor should be a cluster singleton.
     *
     * @return Whether the actor should be a cluster singleton
     */
    public boolean isClusterSingleton() {
        return isClusterSingleton;
    }

    /**
     * Builder for creating {@link SpringActorSpawnContext} instances.
     *
     * @param <A> The type of the actor
     * @param <C> The type of commands that the actor can handle
     */
    public static class Builder<A extends SpringActor<A, C>, C> {
        private Class<A> actorClass;
        private SpringActorContext actorContext;
        private Duration duration = Duration.ofSeconds(3);
        private MailboxSelector mailboxSelector = MailboxSelector.defaultMailbox();
        private boolean isClusterSingleton = false;

        /**
         * Sets the class of the actor to spawn.
         *
         * @param actorClass The class of the actor to spawn
         * @return This builder
         */
        public Builder<A, C> actorClass(Class<A> actorClass) {
            this.actorClass = actorClass;
            return this;
        }

        /**
         * Sets the ID of the actor to spawn. This creates a default actor context with the given ID.
         *
         * @param actorId The ID of the actor to spawn
         * @return This builder
         */
        public Builder<A, C> actorId(String actorId) {
            this.actorContext = new DefaultSpringActorContext(actorId);
            return this;
        }

        /**
         * Sets the context of the actor to spawn.
         *
         * @param actorContext The context of the actor to spawn
         * @return This builder
         */
        public Builder<A, C> actorContext(SpringActorContext actorContext) {
            this.actorContext = actorContext;
            return this;
        }

        /**
         * Sets the timeout for the spawn operation.
         *
         * @param duration The timeout for the spawn operation
         * @return This builder
         */
        public Builder<A, C> duration(Duration duration) {
            this.duration = duration;
            return this;
        }

        /**
         * Sets the mailbox selector to use for the actor.
         *
         * @param mailboxSelector The mailbox selector to use for the actor
         * @return This builder
         */
        public Builder<A, C> mailboxSelector(MailboxSelector mailboxSelector) {
            this.mailboxSelector = mailboxSelector;
            return this;
        }

        /**
         * Sets whether the actor should be a cluster singleton.
         *
         * @param isClusterSingleton Whether the actor should be a cluster singleton
         * @return This builder
         */
        public Builder<A, C> isClusterSingleton(boolean isClusterSingleton) {
            this.isClusterSingleton = isClusterSingleton;
            return this;
        }

        /**
         * Builds a new {@link SpringActorSpawnContext} with the parameters set in this builder.
         *
         * @return A new {@link SpringActorSpawnContext}
         * @throws IllegalArgumentException If actorClass or actorContext is null
         */
        public SpringActorSpawnContext<A, C> build() {
            if (actorClass == null || actorContext == null) {
                throw new IllegalArgumentException("actorClass and actorContext must be set");
            }
            return new SpringActorSpawnContext<>(
                    actorClass,
                    actorContext,
                    duration,
                    mailboxSelector,
                    isClusterSingleton
            );
        }
    }
}

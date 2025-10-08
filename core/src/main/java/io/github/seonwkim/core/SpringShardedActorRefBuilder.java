package io.github.seonwkim.core;

import java.time.Duration;
import org.apache.pekko.actor.typed.Scheduler;
import org.apache.pekko.cluster.sharding.typed.javadsl.EntityRef;

/**
 * Builder for creating instances of {@link SpringShardedActorRef}. This builder provides a fluent
 * API for constructing SpringShardedActorRef instances with various configurations.
 *
 * @param <T> The type of messages that the actor can handle
 */
public class SpringShardedActorRefBuilder<T> {
    private final Scheduler scheduler;
    private final EntityRef<T> entityRef;
    private Duration timeout = Duration.ofSeconds(SpringShardedActorRef.DEFAULT_TIMEOUT_SECONDS);

    /**
     * Creates a new builder with the required parameters.
     *
     * @param scheduler The scheduler to use for asking messages
     * @param entityRef The entity reference to wrap
     */
    public SpringShardedActorRefBuilder(Scheduler scheduler, EntityRef<T> entityRef) {
        this.scheduler = scheduler;
        this.entityRef = entityRef;
    }

    /**
     * Sets the timeout for ask operations.
     *
     * @param timeout The timeout duration
     * @return This builder for method chaining
     */
    public SpringShardedActorRefBuilder<T> withTimeout(Duration timeout) {
        this.timeout = timeout;
        return this;
    }

    /**
     * Sets the timeout for ask operations in seconds.
     *
     * @param timeoutSeconds The timeout in seconds
     * @return This builder for method chaining
     */
    public SpringShardedActorRefBuilder<T> withTimeoutSeconds(int timeoutSeconds) {
        return withTimeout(Duration.ofSeconds(timeoutSeconds));
    }

    /**
     * Builds a new SpringShardedActorRef instance with the configured parameters.
     *
     * @return A new SpringShardedActorRef instance
     */
    public SpringShardedActorRef<T> build() {
        return new SpringShardedActorRef<>(scheduler, entityRef, timeout);
    }
}

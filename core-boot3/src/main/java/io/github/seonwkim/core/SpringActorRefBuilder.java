package io.github.seonwkim.core;

import java.time.Duration;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Scheduler;

/**
 * Builder for creating instances of {@link SpringActorHandle}. This builder provides a fluent API for
 * constructing SpringActorHandle instances with various configurations.
 *
 * @param <T> The type of messages that the actor can handle
 */
public class SpringActorRefBuilder<T> {
    private final Scheduler scheduler;
    private final ActorRef<T> actorRef;
    private Duration timeout = ActorConstants.DEFAULT_TIMEOUT;

    /**
     * Creates a new builder with the required parameters.
     *
     * @param scheduler The scheduler to use for asking messages
     * @param actorRef The actor reference to wrap
     */
    public SpringActorRefBuilder(Scheduler scheduler, ActorRef<T> actorRef) {
        this.scheduler = scheduler;
        this.actorRef = actorRef;
    }

    /**
     * Sets the timeout for ask operations.
     *
     * @param timeout The timeout duration
     * @return This builder for method chaining
     */
    public SpringActorRefBuilder<T> withTimeout(Duration timeout) {
        this.timeout = timeout;
        return this;
    }

    /**
     * Sets the timeout for ask operations in seconds.
     *
     * @param timeoutSeconds The timeout in seconds
     * @return This builder for method chaining
     */
    public SpringActorRefBuilder<T> withTimeoutSeconds(int timeoutSeconds) {
        return withTimeout(Duration.ofSeconds(timeoutSeconds));
    }

    /**
     * Builds a new SpringActorHandle instance with the configured parameters.
     *
     * @return A new SpringActorHandle instance
     */
    public SpringActorHandle<T> build() {
        return new SpringActorHandle<>(scheduler, actorRef, timeout);
    }
}

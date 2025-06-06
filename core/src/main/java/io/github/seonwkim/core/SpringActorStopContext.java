package io.github.seonwkim.core;

import java.time.Duration;

import io.github.seonwkim.core.impl.DefaultSpringActorContext;

/**
 * Context for stopping an actor. This class encapsulates all the parameters needed to stop an actor.
 * It is used by the {@link SpringActorSystem#stop(SpringActorStopContext)} method to stop an existing actor.
 *
 * @param <A> The type of the actor
 * @param <C> The type of commands that the actor can handle
 */
public class SpringActorStopContext<A extends SpringActor<A, C>, C> {
    private final Class<A> actorClass;
    private final SpringActorContext actorContext;
    private final Duration timeout;

    /**
     * Creates a new SpringActorStopContext with the given parameters.
     *
     * @param actorClass The class of the actor to stop
     * @param actorContext The context of the actor to stop
     * @param timeout The timeout for the stop operation
     */
    public SpringActorStopContext(
            Class<A> actorClass,
            SpringActorContext actorContext,
            Duration timeout) {
        this.actorClass = actorClass;
        this.actorContext = actorContext;
        this.timeout = timeout;
    }

    /**
     * Returns the class of the actor to stop.
     *
     * @return The class of the actor to stop
     */
    public Class<A> getActorClass() {
        return actorClass;
    }

    /**
     * Returns the context of the actor to stop.
     *
     * @return The context of the actor to stop
     */
    public SpringActorContext getActorContext() {
        return actorContext;
    }

    /**
     * Returns the timeout for the stop operation.
     *
     * @return The timeout for the stop operation
     */
    public Duration getTimeout() {
        return timeout;
    }

    /**
     * Builder for creating {@link SpringActorStopContext} instances.
     *
     * @param <A> The type of the actor
     * @param <C> The type of commands that the actor can handle
     */
    public static class Builder<A extends SpringActor<A, C>, C> {
        private Class<A> actorClass;
        private SpringActorContext actorContext;
        private Duration duration = Duration.ofSeconds(3);

        /**
         * Sets the class of the actor to stop.
         *
         * @param actorClass The class of the actor to stop
         * @return This builder
         */
        public Builder<A, C> actorClass(Class<A> actorClass) {
            this.actorClass = actorClass;
            return this;
        }

        /**
         * Sets the actor ID for the actor to stop. This creates a default actor context with the given ID.
         *
         * @param actorId The ID of the actor to stop
         * @return This builder
         */
        public Builder<A, C> actorId(String actorId) {
            this.actorContext = new DefaultSpringActorContext(actorId);
            return this;
        }

        /**
         * Sets the actor context for the actor to stop.
         *
         * @param actorContext The context of the actor to stop
         * @return This builder
         */
        public Builder<A, C> actorContext(SpringActorContext actorContext) {
            this.actorContext = actorContext;
            return this;
        }

        /**
         * Sets the timeout for the stop operation.
         *
         * @param duration The maximum time to wait for the stop operation to complete
         * @return This builder
         */
        public Builder<A, C> duration(Duration duration) {
            this.duration = duration;
            return this;
        }

        /**
         * Builds a new {@link SpringActorStopContext} with the parameters set in this builder.
         *
         * @return A new {@link SpringActorStopContext}
         * @throws IllegalArgumentException If actorClass or actorContext is null
         */
        public SpringActorStopContext<A, C> build() {
            if (actorClass == null || actorContext == null) {
                throw new IllegalArgumentException("actorClass and actorContext must be set");
            }
            return new SpringActorStopContext<>(
                    actorClass,
                    actorContext,
                    duration
            );
        }
    }
}

package io.github.seonwkim.core;

import java.time.Duration;

import io.github.seonwkim.core.impl.DefaultSpringActorContext;

/**
 * Context for stopping an actor. This class encapsulates all the parameters needed to stop an actor.
 *
 * @param <C> The type of commands that the actor can handle
 */
public class SpringActorStopContext<A extends SpringActor<A, C>, C> {
    private final Class<A> actorClass;
    private final SpringActorContext actorContext;
    private final Duration duration;

    public SpringActorStopContext(
            Class<A> actorClass,
            SpringActorContext actorContext,
            Duration duration) {
        this.actorClass = actorClass;
        this.actorContext = actorContext;
        this.duration = duration;
    }

    public Class<A> getActorClass() {
        return actorClass;
    }

    public SpringActorContext getActorContext() {
        return actorContext;
    }

    public Duration getDuration() {
        return duration;
    }

    /**
     * Builder for creating {@link SpringActorStopContext} instances.
     *
     * @param <C> The type of commands that the actor can handle
     */
    public static class Builder<A extends SpringActor<A, C>, C> {
        private Class<A> actorClass;
        private SpringActorContext actorContext;
        private Duration duration = Duration.ofSeconds(3);

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
         * @throws IllegalArgumentException If commandClass or actorContext is null
         */
        public SpringActorStopContext<A, C> build() {
            if (actorClass == null || actorContext == null) {
                throw new IllegalArgumentException("commandClass and actorContext must be set");
            }
            return new SpringActorStopContext<>(
                    actorClass,
                    actorContext,
                    duration
            );
        }
    }
}

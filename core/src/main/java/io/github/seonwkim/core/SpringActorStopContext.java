package io.github.seonwkim.core;

import java.time.Duration;

import io.github.seonwkim.core.impl.DefaultSpringActorContext;

/**
 * Context for stopping an actor. This class encapsulates all the parameters needed to stop an actor.
 *
 * @param <T> The type of commands that the actor can handle
 */
public class SpringActorStopContext<T> {
    private final Class<T> commandClass;
    private final SpringActorContext actorContext;
    private final Duration duration;

    public SpringActorStopContext(
            Class<T> commandClass,
            SpringActorContext actorContext,
            Duration duration) {
        this.commandClass = commandClass;
        this.actorContext = actorContext;
        this.duration = duration;
    }

    public Class<T> getCommandClass() {
        return commandClass;
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
     * @param <T> The type of commands that the actor can handle
     */
    public static class Builder<T> {
        private Class<T> commandClass;
        private SpringActorContext actorContext;
        private Duration duration = Duration.ofSeconds(3);

        /**
         * Sets the command class for the actor to stop.
         *
         * @param commandClass The class of commands that the actor can handle
         * @return This builder
         */
        public Builder<T> commandClass(Class<T> commandClass) {
            this.commandClass = commandClass;
            return this;
        }

        /**
         * Sets the actor ID for the actor to stop. This creates a default actor context with the given ID.
         *
         * @param actorId The ID of the actor to stop
         * @return This builder
         */
        public Builder<T> actorId(String actorId) {
            this.actorContext = new DefaultSpringActorContext(actorId);
            return this;
        }

        /**
         * Sets the actor context for the actor to stop.
         *
         * @param actorContext The context of the actor to stop
         * @return This builder
         */
        public Builder<T> actorContext(SpringActorContext actorContext) {
            this.actorContext = actorContext;
            return this;
        }

        /**
         * Sets the timeout for the stop operation.
         *
         * @param duration The maximum time to wait for the stop operation to complete
         * @return This builder
         */
        public Builder<T> duration(Duration duration) {
            this.duration = duration;
            return this;
        }

        /**
         * Builds a new {@link SpringActorStopContext} with the parameters set in this builder.
         *
         * @return A new {@link SpringActorStopContext}
         * @throws IllegalArgumentException If commandClass or actorContext is null
         */
        public SpringActorStopContext<T> build() {
            if (commandClass == null || actorContext == null) {
                throw new IllegalArgumentException("commandClass and actorContext must be set");
            }
            return new SpringActorStopContext<>(
                    commandClass,
                    actorContext,
                    duration
            );
        }
    }
}

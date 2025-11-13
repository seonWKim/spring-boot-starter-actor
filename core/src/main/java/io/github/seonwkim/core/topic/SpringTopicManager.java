package io.github.seonwkim.core.topic;

import io.github.seonwkim.core.SpringActorSystem;
import io.github.seonwkim.core.SpringBehaviorContext;

import javax.annotation.Nullable;

/**
 * Service for managing pub/sub topics with Spring DI.
 * Topics can be unbounded (system-wide) or bounded to a specific actor.
 */
public class SpringTopicManager {

    private final SpringActorSystem actorSystem;

    public SpringTopicManager(SpringActorSystem actorSystem) {
        this.actorSystem = actorSystem;
    }

    /**
     * Start building a topic configuration.
     *
     * @param messageType The type of messages this topic will handle
     * @return A builder for configuring the topic
     */
    public <T> TopicBuilder<T> topic(Class<T> messageType) {
        return new TopicBuilder<>(messageType, actorSystem);
    }

    /**
     * Builder for configuring and creating topics.
     */
    public static class TopicBuilder<T> {
        private final Class<T> messageType;
        private final SpringActorSystem actorSystem;
        @Nullable
        private String name;
        @Nullable
        private SpringBehaviorContext<?> ownerContext;

        TopicBuilder(Class<T> messageType, SpringActorSystem actorSystem) {
            this.messageType = messageType;
            this.actorSystem = actorSystem;
            this.name = null;
            this.ownerContext = null;
        }

        /**
         * Sets the topic name.
         *
         * @param name Unique topic name
         * @return This builder
         */
        public TopicBuilder<T> withName(String name) {
            this.name = name;
            return this;
        }

        /**
         * Binds this topic to an actor's lifecycle.
         * The topic will be stopped when the owning actor stops.
         *
         * @param ctx The behavior context of the owning actor
         * @return This builder
         */
        public TopicBuilder<T> ownedBy(SpringBehaviorContext<?> ctx) {
            this.ownerContext = ctx;
            return this;
        }

        /**
         * Creates the topic.
         * If ownedBy() was called, topic is bounded to that actor.
         * Otherwise, topic is unbounded (system-wide).
         *
         * @return Reference to the created topic
         */
        public SpringTopicRef<T> create() {
            if (name == null || name.isEmpty()) {
                throw new IllegalArgumentException("Topic name must be specified");
            }

            if (ownerContext != null) {
                // Bounded topic - spawn as child of the owning actor
                return TopicSpawner.createTopic(ownerContext.getUnderlying(), messageType, name);
            } else {
                // Unbounded topic - create at system level
                return TopicSpawner.createTopic(actorSystem.getRaw(), messageType, name);
            }
        }

        /**
         * Gets or creates the topic with idempotent semantics.
         * If ownedBy() was called, uses that actor's context.
         * Otherwise, uses the actor system.
         *
         * @return Reference to the topic
         */
        public SpringTopicRef<T> getOrCreate() {
            if (name == null || name.isEmpty()) {
                throw new IllegalArgumentException("Topic name must be specified");
            }

            if (ownerContext != null) {
                // Bounded topic - get or create in the owning actor's context
                return TopicSpawner.getOrCreateTopic(ownerContext.getUnderlying(), messageType, name);
            } else {
                // Unbounded topic - create at system level (no getOrCreate for system level)
                return TopicSpawner.createTopic(actorSystem.getRaw(), messageType, name);
            }
        }
    }
}

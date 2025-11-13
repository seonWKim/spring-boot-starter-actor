package io.github.seonwkim.core.topic;

import io.github.seonwkim.core.RootGuardian;
import org.apache.pekko.actor.typed.ActorSystem;
import io.github.seonwkim.core.SpringBehaviorContext;

/**
 * Fluent builder for creating pub/sub topics at the system level.
 *
 * <p>This builder provides a convenient API for creating topics that are managed
 * by the RootGuardian actor, making them system-wide and not tied to any particular
 * actor's lifecycle.
 *
 * <p>Example usage:
 * <pre>
 * {@code
 * // Create a new topic
 * SpringTopicRef<SystemEvent> topic = actorSystem
 *     .topic(SystemEvent.class)
 *     .withName("system-events")
 *     .create();
 *
 * // Get or create (idempotent)
 * SpringTopicRef<SystemEvent> topic = actorSystem
 *     .topic(SystemEvent.class)
 *     .withName("system-events")
 *     .getOrCreate();
 * }
 * </pre>
 *
 * @param <M> The type of messages this topic will handle
 */
public class SpringTopicBuilder<M> {

    private final ActorSystem<RootGuardian.Command> actorSystem;
    private final Class<M> messageType;
    @javax.annotation.Nullable
    private String topicName;

    /**
     * Creates a new SpringTopicBuilder.
     *
     * @param actorSystem The actor system
     * @param messageType The type of messages this topic will handle
     */
    public SpringTopicBuilder(ActorSystem<RootGuardian.Command> actorSystem, Class<M> messageType) {
        this.actorSystem = actorSystem;
        this.messageType = messageType;
    }

    /**
     * Sets the name for this topic.
     *
     * @param topicName The unique name for the topic
     * @return This builder for method chaining
     */
    public SpringTopicBuilder<M> withName(String topicName) {
        this.topicName = topicName;
        return this;
    }

    /**
     * Creates a new topic with the configured name.
     *
     * <p>Note: This will fail with InvalidActorNameException if a topic actor
     * with the same name already exists. For actor-context-owned topics, use
     * {@link SpringBehaviorContext#getOrCreateTopic} for idempotent creation.
     *
     * @return A reference to the created topic
     * @throws IllegalStateException if topic name is not set
     * @throws org.apache.pekko.actor.InvalidActorNameException if a topic with this name exists
     */
    public SpringTopicRef<M> create() {
        String validatedName = validateTopicName();
        return TopicSpawner.createTopic(actorSystem, messageType, validatedName);
    }

    private String validateTopicName() {
        if (topicName == null || topicName.isEmpty()) {
            throw new IllegalStateException("Topic name must be set using withName()");
        }
        return topicName;
    }
}

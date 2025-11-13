package io.github.seonwkim.core.topic;

import io.github.seonwkim.core.RootGuardian;
import org.apache.pekko.actor.typed.ActorSystem;
import io.github.seonwkim.core.SpringBehaviorContext;

/**
 * Fluent builder for creating pub/sub topics via ActorSystem.
 *
 * <p>Topics created this way persist for the ActorSystem lifetime.
 * For actor-owned topics, use {@link SpringBehaviorContext#createTopic}.
 *
 * <p>Example:
 * <pre>{@code
 * SpringTopicRef<Event> topic = actorSystem
 *     .topic(Event.class)
 *     .withName("events")
 *     .create();
 * }</pre>
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
     * Creates a new topic. Fails if the name is already in use.
     *
     * @return A reference to the created topic
     * @throws IllegalStateException if topic name not set
     * @throws org.apache.pekko.actor.InvalidActorNameException if name already exists
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

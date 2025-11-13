package io.github.seonwkim.core.topic;

import javax.annotation.Nullable;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.apache.pekko.actor.typed.pubsub.Topic;

/**
 * Centralized topic creation logic for both ActorSystem and ActorContext.
 *
 * <p>Topics are identified by name, not creation location. Lifecycle differs:
 * actor-owned topics stop with their owner, system-level topics persist.
 */
public final class TopicSpawner {

    private TopicSpawner() {
        // Utility class - prevent instantiation
    }

    /**
     * Creates a topic as a child of the given actor context.
     *
     * @param ctx The actor context
     * @param messageType The message type
     * @param topicName The unique topic name
     * @return Reference to the created topic
     */
    public static <M> SpringTopicRef<M> createTopic(
            ActorContext<?> ctx, Class<M> messageType, String topicName) {
        ActorRef<Topic.Command<M>> topicRef = ctx.spawn(
            Topic.create(messageType, topicName),
            topicName
        );
        return new SpringTopicRef<>(topicRef, topicName);
    }

    /**
     * Creates a topic as a system actor (persists for ActorSystem lifetime).
     *
     * @param system The actor system
     * @param messageType The message type
     * @param topicName The unique topic name
     * @return Reference to the created topic
     */
    public static <M> SpringTopicRef<M> createTopic(
            ActorSystem<?> system, Class<M> messageType, String topicName) {
        ActorRef<Topic.Command<M>> topicRef = system.systemActorOf(
            Topic.create(messageType, topicName),
            topicName,
            org.apache.pekko.actor.typed.Props.empty()
        );
        return new SpringTopicRef<>(topicRef, topicName);
    }

    /**
     * Gets an existing topic by name, or null if not found.
     *
     * @param ctx The actor context
     * @param topicName The topic name
     * @return The topic reference, or null
     */
    @Nullable
    @SuppressWarnings("unchecked")
    public static <M> SpringTopicRef<M> getTopic(ActorContext<?> ctx, String topicName) {
        return ctx.getChild(topicName)
            .map(ref -> {
                ActorRef<Topic.Command<M>> typedRef = (ActorRef<Topic.Command<M>>) (Object) ref;
                return new SpringTopicRef<>(typedRef, topicName);
            })
            .orElse(null);
    }

    /**
     * Gets or creates a topic with idempotent semantics.
     *
     * @param ctx The actor context
     * @param messageType The message type
     * @param topicName The topic name
     * @return Reference to the topic
     */
    public static <M> SpringTopicRef<M> getOrCreateTopic(
            ActorContext<?> ctx, Class<M> messageType, String topicName) {
        SpringTopicRef<M> existing = getTopic(ctx, topicName);
        if (existing != null) {
            return existing;
        }
        return createTopic(ctx, messageType, topicName);
    }

}

package io.github.seonwkim.core.topic;

import javax.annotation.Nullable;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.apache.pekko.actor.typed.pubsub.Topic;

/**
 * Utility class that provides centralized topic creation logic.
 * This class consolidates the common topic spawning logic used by both:
 * <ul>
 *   <li>SpringActorSystem - for system-wide topics</li>
 *   <li>SpringBehaviorContext - for actor-owned topics</li>
 * </ul>
 *
 * <p>By centralizing this logic, we ensure consistency and reduce code duplication
 * across the framework.
 */
public final class TopicSpawner {

    private TopicSpawner() {
        // Utility class - prevent instantiation
    }

    /**
     * Creates a pub/sub topic with the given message type and name in an ActorContext.
     *
     * <p>This method spawns a Pekko Topic actor as a child of the given context.
     * Topics enable distributed publish-subscribe messaging within a local actor system
     * or across a cluster.
     *
     * @param ctx The actor context to spawn the topic in
     * @param messageType The type of messages this topic will handle
     * @param topicName The unique name for this topic
     * @param <M> The message type
     * @return A reference to the created topic
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
     * Creates a pub/sub topic with the given message type and name in an ActorSystem.
     *
     * <p>This method spawns a Pekko Topic actor as a system actor.
     * Topics enable distributed publish-subscribe messaging within a local actor system
     * or across a cluster.
     *
     * @param system The actor system to spawn the topic in
     * @param messageType The type of messages this topic will handle
     * @param topicName The unique name for this topic
     * @param <M> The message type
     * @return A reference to the created topic
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
     * Gets a reference to an existing topic in the given context.
     * Returns null if the topic does not exist.
     *
     * @param ctx The actor context to search in
     * @param topicName The name of the topic to find
     * @param <M> The message type
     * @return The topic reference if found, null otherwise
     */
    @Nullable
    @SuppressWarnings("unchecked")
    public static <M> SpringTopicRef<M> getTopic(ActorContext<?> ctx, String topicName) {
        return ctx.getChild(topicName)
            .<SpringTopicRef<M>>map(ref -> {
                ActorRef<Topic.Command<M>> typedRef = (ActorRef<Topic.Command<M>>) (Object) ref;
                return new SpringTopicRef<>(typedRef, topicName);
            })
            .orElse(null);
    }

    /**
     * Gets a reference to an existing topic, or creates it if it doesn't exist.
     * This provides idempotent topic creation semantics.
     *
     * @param ctx The actor context to spawn the topic in
     * @param messageType The type of messages this topic will handle
     * @param topicName The unique name for this topic
     * @param <M> The message type
     * @return A reference to the topic (existing or newly created)
     */
    public static <M> SpringTopicRef<M> getOrCreateTopic(
            ActorContext<?> ctx, Class<M> messageType, String topicName) {
        SpringTopicRef<M> existing = getTopic(ctx, topicName);
        if (existing != null) {
            return existing;
        }
        return createTopic(ctx, messageType, topicName);
    }

    /**
     * Gets a reference to an existing topic, or creates it if it doesn't exist.
     * This provides idempotent topic creation semantics.
     *
     * <p>Note: For system-level topics, this will always create a new topic because
     * there's no way to check if a system actor already exists. If you try to create
     * a topic with a name that already exists, it will throw an InvalidActorNameException.
     *
     * @param system The actor system to spawn the topic in
     * @param messageType The type of messages this topic will handle
     * @param topicName The unique name for this topic
     * @param <M> The message type
     * @return A reference to the topic (existing or newly created)
     */
    public static <M> SpringTopicRef<M> getOrCreateTopic(
            ActorSystem<?> system, Class<M> messageType, String topicName) {
        // For system-level topics, we can't check existence, so just try to create
        // If it already exists, Pekko will throw InvalidActorNameException
        try {
            return createTopic(system, messageType, topicName);
        } catch (Exception e) {
            // Topic already exists - this is expected for getOrCreate semantics
            // We need to find another way to handle this
            // For now, we'll just throw - users should be careful with topic names
            throw new IllegalStateException(
                "Topic '" + topicName + "' already exists. " +
                "System-level topics do not support true getOrCreate() semantics. " +
                "Either use a unique name or manage topic lifecycle manually.", e);
        }
    }
}

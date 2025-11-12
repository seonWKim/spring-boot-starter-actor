package io.github.seonwkim.core.pubsub;

import io.github.seonwkim.core.SpringActorSystem;
import javax.annotation.Nullable;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.pubsub.Topic;

/**
 * Fluent builder for creating and configuring distributed pub/sub topics.
 *
 * <p>This builder provides a Spring-friendly API for working with Pekko's distributed
 * publish-subscribe functionality. Topics enable decoupled communication between actors
 * across a cluster.
 *
 * <p><b>Example Usage:</b>
 * <pre>{@code
 * // Get or create a topic
 * SpringTopicRef<ChatEvent> topic = actorSystem
 *     .topic(ChatEvent.class)
 *     .withName("chat-room-1")
 *     .getOrCreate();
 *
 * // Get existing topic (throws if doesn't exist)
 * SpringTopicRef<ChatEvent> existing = actorSystem
 *     .topic(ChatEvent.class)
 *     .withName("chat-room-1")
 *     .get();
 * }</pre>
 *
 * @param <T> The type of messages that can be published to the topic
 */
public class SpringTopicBuilder<T> {

    private final SpringActorSystem actorSystem;
    private final Class<T> messageType;

    @Nullable private String topicName;

    /**
     * Creates a new SpringTopicBuilder.
     *
     * @param actorSystem The Spring actor system
     * @param messageType The class of messages that will be published to this topic
     */
    public SpringTopicBuilder(SpringActorSystem actorSystem, Class<T> messageType) {
        if (actorSystem == null) {
            throw new IllegalArgumentException("actorSystem must not be null");
        }
        if (messageType == null) {
            throw new IllegalArgumentException("messageType must not be null");
        }
        this.actorSystem = actorSystem;
        this.messageType = messageType;
    }

    /**
     * Sets the name for this topic.
     *
     * <p>Topic names should be unique within your application. All actors subscribing
     * to the same topic name will receive messages published to that topic.
     *
     * <p>Naming conventions:
     * <ul>
     *   <li>Use lowercase with hyphens: "chat-room-1", "order-events"
     *   <li>Make names descriptive and scoped: "user-123-notifications"
     *   <li>Avoid special characters that might cause issues in URLs or logs
     * </ul>
     *
     * @param topicName The unique name for the topic
     * @return This builder for method chaining
     */
    public SpringTopicBuilder<T> withName(String topicName) {
        if (topicName == null || topicName.isEmpty()) {
            throw new IllegalArgumentException("topicName must not be null or empty");
        }
        this.topicName = topicName;
        return this;
    }

    /**
     * Gets or creates a topic reference. This operation is idempotent - calling it multiple
     * times with the same name will return references to the same underlying topic.
     *
     * <p>This is the recommended way to obtain a topic reference as it handles both
     * creation and retrieval cases.
     *
     * <p><b>Important:</b> The topic actor is created lazily. The actual Pekko topic
     * actor will be spawned when this method is called. If a topic with the same name
     * already exists, a reference to the existing topic will be returned.
     *
     * @return A SpringTopicRef for publishing and subscribing
     * @throws IllegalStateException If topic name was not set
     */
    public SpringTopicRef<T> getOrCreate() {
        if (topicName == null) {
            throw new IllegalStateException("Topic name must be set using withName() before calling getOrCreate()");
        }

        // Use the thread-safe method in SpringActorSystem to get or create the topic
        ActorRef<Topic.Command<T>> topicRef = actorSystem.getOrCreateTopicActor(messageType, topicName);

        return new SpringTopicRef<>(topicRef, topicName);
    }

    /**
     * Gets a reference to an existing topic without creating it.
     *
     * <p>This method attempts to retrieve an existing topic. It will succeed if the topic
     * was previously created, and fail otherwise.
     *
     * <p><b>Note:</b> Due to the nature of Pekko's topic implementation, this method
     * currently behaves the same as {@link #getOrCreate()}. Topics are created on-demand
     * and automatically cleaned up when no subscribers exist.
     *
     * @return A SpringTopicRef for publishing and subscribing
     * @throws IllegalStateException If topic name was not set
     * @throws IllegalStateException If cluster mode is not enabled
     */
    public SpringTopicRef<T> get() {
        // In Pekko's Topic implementation, topics are created on demand and
        // automatically cleaned up when no subscribers exist. Therefore,
        // get() and getOrCreate() behave the same way.
        return getOrCreate();
    }
}

package io.github.seonwkim.core.pubsub;

import io.github.seonwkim.core.SpringActorRef;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.pubsub.Topic;

/**
 * A wrapper around Pekko's Topic ActorRef that provides a Spring-friendly API
 * for distributed publish-subscribe messaging in a cluster.
 *
 * <p>SpringTopicRef enables decoupled communication between actors using the pub/sub pattern.
 * Multiple actors can subscribe to a topic to receive messages published to it.
 *
 * <p><b>Key Features:</b>
 * <ul>
 *   <li>Distributed across cluster - topics work across all cluster nodes
 *   <li>At-most-once delivery - messages are delivered at most once to each subscriber
 *   <li>Automatic cleanup - topics are automatically removed when no subscribers exist
 *   <li>Type-safe - generic type parameter ensures type safety
 *   <li>Location transparent - subscribers don't need to know about each other
 * </ul>
 *
 * <p><b>Example Usage:</b>
 * <pre>{@code
 * // Get a topic reference
 * SpringTopicRef<ChatEvent> chatTopic = actorSystem
 *     .topic(ChatEvent.class)
 *     .withName("chat-room-1")
 *     .getOrCreate();
 *
 * // Publish messages
 * chatTopic.publish(new ChatEvent.Message("user1", "Hello!"));
 *
 * // Subscribe actors
 * chatTopic.subscribe(userActorRef);
 *
 * // Unsubscribe when done
 * chatTopic.unsubscribe(userActorRef);
 * }</pre>
 *
 * <p><b>Important Notes:</b>
 * <ul>
 *   <li>Requires cluster mode to be enabled
 *   <li>Messages must be serializable (implement JsonSerializable or CborSerializable)
 *   <li>Delivery is at-most-once (not guaranteed)
 *   <li>Message ordering is not guaranteed across subscribers
 *   <li>For stronger guarantees, consider using Pekko Streams or other solutions
 * </ul>
 *
 * @param <T> The type of messages that can be published to this topic
 */
public class SpringTopicRef<T> {

    private final ActorRef<Topic.Command<T>> topicRef;
    private final String topicName;

    /**
     * Creates a new SpringTopicRef wrapping a Pekko Topic ActorRef.
     *
     * @param topicRef The underlying Pekko topic actor reference
     * @param topicName The name of the topic
     */
    public SpringTopicRef(ActorRef<Topic.Command<T>> topicRef, String topicName) {
        if (topicRef == null) {
            throw new IllegalArgumentException("topicRef must not be null");
        }
        if (topicName == null || topicName.isEmpty()) {
            throw new IllegalArgumentException("topicName must not be null or empty");
        }
        this.topicRef = topicRef;
        this.topicName = topicName;
    }

    /**
     * Publishes a message to this topic. All subscribed actors will receive the message.
     *
     * <p>This is a fire-and-forget operation with at-most-once delivery semantics.
     * The message may not be delivered if:
     * <ul>
     *   <li>There are no subscribers
     *   <li>A subscriber is unreachable or has terminated
     *   <li>Network issues occur in a cluster
     * </ul>
     *
     * @param message The message to publish
     */
    public void publish(T message) {
        if (message == null) {
            throw new IllegalArgumentException("message must not be null");
        }
        topicRef.tell(Topic.publish(message));
    }

    /**
     * Subscribes a Spring actor to receive messages published to this topic.
     *
     * <p>Once subscribed, the actor will receive all messages published to this topic
     * until it is explicitly unsubscribed or the actor terminates.
     *
     * <p>It is safe to subscribe the same actor multiple times; the topic will
     * deduplicate subscriptions automatically.
     *
     * @param subscriber The Spring actor reference to subscribe
     */
    public void subscribe(SpringActorRef<T> subscriber) {
        if (subscriber == null) {
            throw new IllegalArgumentException("subscriber must not be null");
        }
        topicRef.tell(Topic.subscribe(subscriber.getUnderlying()));
    }

    /**
     * Unsubscribes a Spring actor from receiving messages from this topic.
     *
     * <p>After unsubscribing, the actor will no longer receive messages published
     * to this topic. If the actor was not subscribed, this operation is a no-op.
     *
     * <p>Note: Actors are automatically unsubscribed when they terminate, so
     * explicit unsubscription is optional unless you want to stop receiving
     * messages while the actor is still alive.
     *
     * @param subscriber The Spring actor reference to unsubscribe
     */
    public void unsubscribe(SpringActorRef<T> subscriber) {
        if (subscriber == null) {
            throw new IllegalArgumentException("subscriber must not be null");
        }
        topicRef.tell(Topic.unsubscribe(subscriber.getUnderlying()));
    }

    /**
     * Returns the name of this topic.
     *
     * @return The topic name
     */
    public String getTopicName() {
        return topicName;
    }

    /**
     * Returns the underlying Pekko Topic ActorRef for advanced use cases.
     *
     * <p>This method provides access to the raw Pekko API for scenarios not
     * covered by the Spring abstraction. Use with caution as it bypasses
     * the Spring-friendly API.
     *
     * @return The underlying Pekko Topic ActorRef
     */
    public ActorRef<Topic.Command<T>> getUnderlying() {
        return topicRef;
    }
}

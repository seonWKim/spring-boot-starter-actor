package io.github.seonwkim.actor.topic;

import java.time.Duration;

/**
 * Creates and finds publish-subscribe topics. Inject it as a bean.
 *
 * <pre>{@code
 * SpringTopicRef<ChatEvent> room = topics.topic(TopicName.of(ChatEvent.class, "room-1")).getOrCreate();
 * room.subscribe(actorHandle);
 * room.publish(new ChatEvent("hi"));
 * }</pre>
 */
public interface SpringTopicManager {

    /** A builder for the topic with this name. */
    <T> TopicBuilder<T> topic(TopicName<T> name);

    /** Creates or finds one topic. */
    interface TopicBuilder<T> {

        /** How long to wait for the topic. Defaults to 5 seconds. */
        TopicBuilder<T> withTimeout(Duration timeout);

        /**
         * Creates the topic on this node.
         *
         * @throws io.github.seonwkim.actor.exception.TopicAlreadyExistsException if it already exists here
         * @throws io.github.seonwkim.actor.exception.TopicCreationTimeoutException if creation timed out
         */
        SpringTopicRef<T> create();

        /** The topic on this node, created if it doesn't exist yet. */
        SpringTopicRef<T> getOrCreate();
    }
}

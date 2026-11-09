package io.github.seonwkim.actor.topic;

import java.util.Objects;

/**
 * Names a publish-subscribe topic and the messages it carries. Publishers and subscribers on every
 * node that use the same name and type share the topic.
 *
 * @param messageType the type of the messages on the topic
 * @param name the topic name
 * @param <T> the type of the messages on the topic
 */
public record TopicName<T>(Class<T> messageType, String name) {

    public TopicName {
        Objects.requireNonNull(messageType, "messageType");
        Objects.requireNonNull(name, "name");
        if (name.isEmpty()) {
            throw new IllegalArgumentException("Topic name must be specified");
        }
    }

    public static <T> TopicName<T> of(Class<T> messageType, String name) {
        return new TopicName<>(messageType, name);
    }
}

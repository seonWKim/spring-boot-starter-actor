package io.github.seonwkim.actor.topic;

import io.github.seonwkim.actor.SpringActorRef;

/**
 * A publish-subscribe topic. Messages published on any node reach the subscribers on every node.
 *
 * @param <T> the type of the messages on the topic
 */
public interface SpringTopicRef<T> {

    /** Sends {@code message} to every subscriber. */
    void publish(T message);

    /** Delivers the topic's messages to {@code subscriber} until it unsubscribes or stops. */
    void subscribe(SpringActorRef<T> subscriber);

    /** Stops delivering the topic's messages to {@code subscriber}. */
    void unsubscribe(SpringActorRef<T> subscriber);

    /** The topic's name and message type. */
    TopicName<T> name();

    /** The engine's topic actor, for features the starter doesn't wrap. */
    <X> X unwrap(Class<X> type);
}

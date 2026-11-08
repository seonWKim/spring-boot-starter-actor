package io.github.seonwkim.actor.internal;

import io.github.seonwkim.actor.SpringActorRef;
import io.github.seonwkim.actor.topic.SpringTopicRef;
import io.github.seonwkim.actor.topic.TopicName;
import java.util.Objects;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.pubsub.Topic;

/** {@link SpringTopicRef} on a Pekko {@link Topic} actor. */
public final class PekkoTopicRef<T> implements SpringTopicRef<T> {

    private final ActorRef<Topic.Command<T>> topic;
    private final TopicName<T> name;

    public PekkoTopicRef(ActorRef<Topic.Command<T>> topic, TopicName<T> name) {
        this.topic = Objects.requireNonNull(topic, "topic");
        this.name = Objects.requireNonNull(name, "name");
    }

    @Override
    public void publish(T message) {
        if (message == null) {
            throw new IllegalArgumentException("message must not be null");
        }
        topic.tell(Topic.publish(message));
    }

    @Override
    public void subscribe(SpringActorRef<T> subscriber) {
        if (subscriber == null) {
            throw new IllegalArgumentException("subscriber must not be null");
        }
        topic.tell(Topic.subscribe(PekkoActorRef.toPekko(subscriber)));
    }

    @Override
    public void unsubscribe(SpringActorRef<T> subscriber) {
        if (subscriber == null) {
            throw new IllegalArgumentException("subscriber must not be null");
        }
        topic.tell(Topic.unsubscribe(PekkoActorRef.toPekko(subscriber)));
    }

    @Override
    public TopicName<T> name() {
        return name;
    }

    @Override
    public <X> X unwrap(Class<X> type) {
        if (type.isInstance(topic)) {
            return type.cast(topic);
        }
        throw new IllegalArgumentException(
                "A topic unwraps to " + ActorRef.class.getName() + ", not " + type.getName());
    }
}

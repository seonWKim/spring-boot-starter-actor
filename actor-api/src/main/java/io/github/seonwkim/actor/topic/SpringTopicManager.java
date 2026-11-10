package io.github.seonwkim.actor.topic;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Creates and finds publish-subscribe topics. Inject it as a bean.
 *
 * <pre>{@code
 * SpringTopicRef<ChatEvent> room = topics.topic(TopicName.of(ChatEvent.class, "room-1")).getOrCreate();
 * room.subscribe(actorHandle);
 * room.publish(new ChatEvent("hi"));
 * }</pre>
 *
 * <p>{@code create()} and {@code getOrCreate()} block the caller until the topic exists. Inside an actor,
 * or anywhere threads are shared, use {@code createAsync()} or {@code getOrCreateAsync()}.
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

        /** The topic on this node, created if it doesn't exist yet. Blocks until it exists. */
        SpringTopicRef<T> getOrCreate();

        /**
         * {@link #create()} without blocking: the stage fails with the same exceptions {@code create()}
         * throws. The default runs {@code create()} on the caller's thread.
         *
         * @since 1.1.0
         */
        default CompletionStage<SpringTopicRef<T>> createAsync() {
            try {
                return CompletableFuture.completedFuture(create());
            } catch (RuntimeException e) {
                return CompletableFuture.failedFuture(e);
            }
        }

        /**
         * {@link #getOrCreate()} without blocking. The default runs {@code getOrCreate()} on the caller's
         * thread.
         *
         * @since 1.1.0
         */
        default CompletionStage<SpringTopicRef<T>> getOrCreateAsync() {
            try {
                return CompletableFuture.completedFuture(getOrCreate());
            } catch (RuntimeException e) {
                return CompletableFuture.failedFuture(e);
            }
        }
    }
}

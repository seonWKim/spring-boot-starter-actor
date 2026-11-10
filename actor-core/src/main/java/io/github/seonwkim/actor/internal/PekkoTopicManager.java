package io.github.seonwkim.actor.internal;

import io.github.seonwkim.actor.SpringActorSystem;
import io.github.seonwkim.actor.exception.TopicAlreadyExistsException;
import io.github.seonwkim.actor.exception.TopicCreationTimeoutException;
import io.github.seonwkim.actor.topic.SpringTopicManager;
import io.github.seonwkim.actor.topic.SpringTopicRef;
import io.github.seonwkim.actor.topic.TopicName;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.actor.typed.javadsl.AskPattern;
import org.apache.pekko.pattern.AskTimeoutException;

/** {@link SpringTopicManager} on Pekko's distributed pub-sub topics, created by the root guardian. */
public final class PekkoTopicManager implements SpringTopicManager {

    private final SpringActorSystem actorSystem;

    public PekkoTopicManager(SpringActorSystem actorSystem) {
        this.actorSystem = actorSystem;
    }

    /**
     * Start building a topic configuration.
     *
     * @param messageType The type of messages this topic will handle
     * @return A builder for configuring the topic
     */
    @Override
    public <T> SpringTopicManager.TopicBuilder<T> topic(TopicName<T> name) {
        return new Builder<>(name.messageType(), name.name(), actorSystem);
    }

    /**
     * Builder for configuring and creating topics.
     */
    private static final class Builder<T> implements SpringTopicManager.TopicBuilder<T> {
        private final Class<T> messageType;
        private final SpringActorSystem actorSystem;

        private final String name;

        private Duration timeout = Duration.ofSeconds(5);

        Builder(Class<T> messageType, String name, SpringActorSystem actorSystem) {
            this.messageType = messageType;
            this.name = name;
            this.actorSystem = actorSystem;
        }

        /**
         * Sets the timeout for topic creation.
         *
         * @param timeout Timeout duration
         * @return This builder
         */
        @Override
        public SpringTopicManager.TopicBuilder<T> withTimeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        @Override
        public SpringTopicRef<T> create() {
            return join(createAsync());
        }

        @Override
        public SpringTopicRef<T> getOrCreate() {
            return join(getOrCreateAsync());
        }

        @Override
        public CompletionStage<SpringTopicRef<T>> createAsync() {
            return ask(replyTo -> new RootGuardian.CreateTopic<>(messageType, name, replyTo))
                    .thenApply(response -> {
                        if (response.alreadyExists) {
                            throw response.errorMessage != null
                                    ? new TopicAlreadyExistsException(name, messageType, response.errorMessage)
                                    : new TopicAlreadyExistsException(name, messageType);
                        }
                        return topicOf(response, "Failed to create topic: ");
                    });
        }

        @Override
        public CompletionStage<SpringTopicRef<T>> getOrCreateAsync() {
            return ask(replyTo -> new RootGuardian.GetOrCreateTopic<>(messageType, name, replyTo))
                    .thenApply(response -> topicOf(response, "Failed to get or create topic: "));
        }

        /** The root guardian's answer; a timeout becomes a {@link TopicCreationTimeoutException}. */
        private CompletionStage<RootGuardian.TopicCreated<T>> ask(
                Function<ActorRef<RootGuardian.TopicCreated<T>>, RootGuardian.Command> request) {
            ActorSystem<RootGuardian.Command> system = PekkoActorSystem.raw(actorSystem);
            CompletableFuture<RootGuardian.TopicCreated<T>> result = new CompletableFuture<>();
            AskPattern.ask(system, request::apply, timeout, system.scheduler()).whenComplete((response, error) -> {
                if (error == null) {
                    result.complete(response);
                } else if (error instanceof TimeoutException || error instanceof AskTimeoutException) {
                    result.completeExceptionally(new TopicCreationTimeoutException(name, messageType, timeout, error));
                } else {
                    result.completeExceptionally(error);
                }
            });
            return result;
        }

        private SpringTopicRef<T> topicOf(RootGuardian.TopicCreated<T> response, String failure) {
            if (!response.isSuccess() || response.topicRef == null) {
                throw new IllegalStateException(
                        failure + name + (response.errorMessage != null ? " - " + response.errorMessage : ""));
            }
            return response.topicRef;
        }

        /** Waits for the stage and throws what it failed with, unwrapped. */
        private SpringTopicRef<T> join(CompletionStage<SpringTopicRef<T>> stage) {
            try {
                return stage.toCompletableFuture().join();
            } catch (CompletionException e) {
                if (e.getCause() instanceof RuntimeException runtime) {
                    throw runtime;
                }
                throw new IllegalStateException("Failed to create topic: " + name, e.getCause());
            }
        }
    }
}

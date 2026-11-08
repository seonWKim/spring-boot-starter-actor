package io.github.seonwkim.actor.internal;

import io.github.seonwkim.actor.SpringActorSystem;
import io.github.seonwkim.actor.exception.TopicAlreadyExistsException;
import io.github.seonwkim.actor.exception.TopicCreationTimeoutException;
import io.github.seonwkim.actor.topic.SpringTopicManager;
import io.github.seonwkim.actor.topic.SpringTopicRef;
import io.github.seonwkim.actor.topic.TopicName;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import org.apache.pekko.actor.typed.ActorRef;
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

        /**
         * Creates the topic.
         *
         * @return Reference to the created topic
         * @throws TopicAlreadyExistsException if the topic already exists
         * @throws TopicCreationTimeoutException if the creation times out
         */
        @Override
        public SpringTopicRef<T> create() {
            String topicName = this.name;

            try {
                RootGuardian.TopicCreated<T> response = AskPattern.ask(
                                PekkoActorSystem.raw(actorSystem),
                                (ActorRef<RootGuardian.TopicCreated<T>> replyTo) ->
                                        new RootGuardian.CreateTopic<>(messageType, topicName, replyTo),
                                timeout,
                                PekkoActorSystem.raw(actorSystem).scheduler())
                        .toCompletableFuture()
                        .get();

                // Check if the topic already exists
                if (response.alreadyExists) {
                    if (response.errorMessage != null) {
                        throw new TopicAlreadyExistsException(topicName, messageType, response.errorMessage);
                    } else {
                        throw new TopicAlreadyExistsException(topicName, messageType);
                    }
                }

                // Check if creation was successful
                if (!response.isSuccess() || response.topicRef == null) {
                    throw new RuntimeException("Failed to create topic: " + topicName
                            + (response.errorMessage != null ? " - " + response.errorMessage : ""));
                }

                return response.topicRef;
            } catch (TopicAlreadyExistsException e) {
                // Re-throw our custom exception
                throw e;
            } catch (ExecutionException e) {
                // Unwrap ExecutionException to check for timeout or other exceptions
                Throwable cause = e.getCause();
                if (cause instanceof AskTimeoutException) {
                    throw new TopicCreationTimeoutException(topicName, messageType, timeout, cause);
                }
                throw new RuntimeException("Failed to create topic: " + topicName, e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Topic creation was interrupted: " + topicName, e);
            } catch (Exception e) {
                throw new RuntimeException("Failed to create topic: " + topicName, e);
            }
        }

        /**
         * Gets or creates the topic with idempotent semantics.
         *
         * @return Reference to the topic
         * @throws TopicCreationTimeoutException if the operation times out
         */
        @Override
        public SpringTopicRef<T> getOrCreate() {
            String topicName = this.name;

            try {
                RootGuardian.TopicCreated<T> response = AskPattern.ask(
                                PekkoActorSystem.raw(actorSystem),
                                (ActorRef<RootGuardian.TopicCreated<T>> replyTo) ->
                                        new RootGuardian.GetOrCreateTopic<>(messageType, topicName, replyTo),
                                timeout,
                                PekkoActorSystem.raw(actorSystem).scheduler())
                        .toCompletableFuture()
                        .get();

                // Check if operation was successful
                if (!response.isSuccess() || response.topicRef == null) {
                    throw new RuntimeException("Failed to get or create topic: " + topicName
                            + (response.errorMessage != null ? " - " + response.errorMessage : ""));
                }

                return response.topicRef;
            } catch (ExecutionException e) {
                // Unwrap ExecutionException to check for timeout or other exceptions
                Throwable cause = e.getCause();
                if (cause instanceof AskTimeoutException) {
                    throw new TopicCreationTimeoutException(topicName, messageType, timeout, cause);
                }
                throw new RuntimeException("Failed to get or create topic: " + topicName, e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Topic get or create was interrupted: " + topicName, e);
            } catch (Exception e) {
                throw new RuntimeException("Failed to get or create topic: " + topicName, e);
            }
        }
    }
}

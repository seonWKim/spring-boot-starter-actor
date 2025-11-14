package io.github.seonwkim.core.topic;

import io.github.seonwkim.core.RootGuardian;
import io.github.seonwkim.core.SpringActorSystem;
import io.github.seonwkim.core.exception.TopicAlreadyExistsException;
import io.github.seonwkim.core.exception.TopicCreationTimeoutException;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import javax.annotation.Nullable;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.javadsl.AskPattern;
import org.apache.pekko.pattern.AskTimeoutException;

/**
 * Service for managing pub/sub topics with Spring DI.
 * All topics are created through the RootGuardian.
 */
public class SpringTopicManager {

    private final SpringActorSystem actorSystem;

    public SpringTopicManager(SpringActorSystem actorSystem) {
        this.actorSystem = actorSystem;
    }

    /**
     * Start building a topic configuration.
     *
     * @param messageType The type of messages this topic will handle
     * @return A builder for configuring the topic
     */
    public <T> TopicBuilder<T> topic(Class<T> messageType) {
        return new TopicBuilder<>(messageType, actorSystem);
    }

    /**
     * Builder for configuring and creating topics.
     */
    public static class TopicBuilder<T> {
        private final Class<T> messageType;
        private final SpringActorSystem actorSystem;

        @Nullable private String name;

        private Duration timeout = Duration.ofSeconds(5);

        TopicBuilder(Class<T> messageType, SpringActorSystem actorSystem) {
            this.messageType = messageType;
            this.actorSystem = actorSystem;
        }

        /**
         * Sets the topic name.
         *
         * @param name Unique topic name
         * @return This builder
         */
        public TopicBuilder<T> withName(String name) {
            this.name = name;
            return this;
        }

        /**
         * Sets the timeout for topic creation.
         *
         * @param timeout Timeout duration
         * @return This builder
         */
        public TopicBuilder<T> withTimeout(Duration timeout) {
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
        public SpringTopicRef<T> create() {
            String topicName = this.name;
            if (topicName == null || topicName.isEmpty()) {
                throw new IllegalArgumentException("Topic name must be specified");
            }

            try {
                RootGuardian.TopicCreated<T> response = AskPattern.ask(
                                actorSystem.getRaw(),
                                (ActorRef<RootGuardian.TopicCreated<T>> replyTo) ->
                                        new RootGuardian.CreateTopic<>(messageType, topicName, replyTo),
                                timeout,
                                actorSystem.getRaw().scheduler())
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
                    throw new RuntimeException("Failed to create topic: " + topicName +
                            (response.errorMessage != null ? " - " + response.errorMessage : ""));
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
        public SpringTopicRef<T> getOrCreate() {
            String topicName = this.name;
            if (topicName == null || topicName.isEmpty()) {
                throw new IllegalArgumentException("Topic name must be specified");
            }

            try {
                RootGuardian.TopicCreated<T> response = AskPattern.ask(
                                actorSystem.getRaw(),
                                (ActorRef<RootGuardian.TopicCreated<T>> replyTo) ->
                                        new RootGuardian.GetOrCreateTopic<>(messageType, topicName, replyTo),
                                timeout,
                                actorSystem.getRaw().scheduler())
                        .toCompletableFuture()
                        .get();

                // Check if operation was successful
                if (!response.isSuccess() || response.topicRef == null) {
                    throw new RuntimeException("Failed to get or create topic: " + topicName +
                            (response.errorMessage != null ? " - " + response.errorMessage : ""));
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

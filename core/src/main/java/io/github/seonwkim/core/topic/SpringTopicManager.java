package io.github.seonwkim.core.topic;

import io.github.seonwkim.core.SpringActorRef;
import io.github.seonwkim.core.SpringActorSystem;
import org.apache.pekko.actor.typed.SupervisorStrategy;

import javax.annotation.Nullable;
import java.time.Duration;

/**
 * Service for managing pub/sub topics with Spring DI.
 * All topics are managed by SpringTopicSpawnActor.
 */
public class SpringTopicManager {

    private final SpringActorSystem actorSystem;
    @Nullable
    private volatile SpringActorRef<SpringTopicSpawnActor.Command> spawnerActorRef;

    public SpringTopicManager(SpringActorSystem actorSystem) {
        this.actorSystem = actorSystem;
    }

    private SpringActorRef<SpringTopicSpawnActor.Command> getOrCreateSpawner() {
        if (spawnerActorRef == null) {
            synchronized (this) {
                if (spawnerActorRef == null) {
                    spawnerActorRef = actorSystem.actor(SpringTopicSpawnActor.class)
                            .withId("spring-topic-spawner")
                            .withSupervisionStrategy(SupervisorStrategy.restart())
                            .spawnAndWait();
                }
            }
        }
        return spawnerActorRef;
    }

    /**
     * Start building a topic configuration.
     *
     * @param messageType The type of messages this topic will handle
     * @return A builder for configuring the topic
     */
    public <T> TopicBuilder<T> topic(Class<T> messageType) {
        return new TopicBuilder<>(messageType, getOrCreateSpawner());
    }

    /**
     * Builder for configuring and creating topics.
     */
    public static class TopicBuilder<T> {
        private final Class<T> messageType;
        private final SpringActorRef<SpringTopicSpawnActor.Command> spawnerActorRef;
        @Nullable
        private String name;

        TopicBuilder(Class<T> messageType, SpringActorRef<SpringTopicSpawnActor.Command> spawnerActorRef) {
            this.messageType = messageType;
            this.spawnerActorRef = spawnerActorRef;
            this.name = null;
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
         * Creates the topic.
         *
         * @return Reference to the created topic
         */
        public SpringTopicRef<T> create() {
            if (name == null || name.isEmpty()) {
                throw new IllegalArgumentException("Topic name must be specified");
            }

            try {
                return spawnerActorRef
                        .ask(new SpringTopicSpawnActor.CreateTopic<>(messageType, name))
                        .withTimeout(Duration.ofSeconds(5))
                        .execute()
                        .toCompletableFuture()
                        .get();
            } catch (Exception e) {
                throw new RuntimeException("Failed to create topic: " + name, e);
            }
        }

        /**
         * Gets or creates the topic with idempotent semantics.
         *
         * @return Reference to the topic
         */
        public SpringTopicRef<T> getOrCreate() {
            if (name == null || name.isEmpty()) {
                throw new IllegalArgumentException("Topic name must be specified");
            }

            try {
                return spawnerActorRef
                        .ask(new SpringTopicSpawnActor.GetOrCreateTopic<>(messageType, name))
                        .withTimeout(Duration.ofSeconds(5))
                        .execute()
                        .toCompletableFuture()
                        .get();
            } catch (Exception e) {
                throw new RuntimeException("Failed to get or create topic: " + name, e);
            }
        }
    }
}

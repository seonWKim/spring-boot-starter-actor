package io.github.seonwkim.core;

import io.github.seonwkim.core.impl.DefaultRootGuardian;
import io.github.seonwkim.core.topic.SpringTopicRef;
import javax.annotation.Nullable;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.SupervisorStrategy;

/**
 * Root guardian interface for the actor system. The root guardian is the top-level actor that
 * manages the lifecycle of all other actors. It handles commands for spawning actors and maintains
 * references to them.
 */
public interface RootGuardian {
    /**
     * Base command type for RootGuardian-compatible actors. All commands sent to the RootGuardian
     * must implement this interface.
     */
    interface Command {}

    /**
     * Command to spawn a new actor. This command is sent to the root guardian to create a new actor
     * of the specified type with the given ID.
     */
    class SpawnActor implements Command {
        public final Class<?> actorClass;
        /** The context of the actor */
        public final SpringActorContext actorContext;
        /** The actor reference to reply to with the spawned actor reference */
        public final ActorRef<Spawned<?>> replyTo;
        /** The mailbox configuration to use * */
        public final MailboxConfig mailboxConfig;
        /** The dispatcher configuration */
        public final DispatcherConfig dispatcherConfig;
        /** The tags configuration for logging/categorization */
        public final TagsConfig tagsConfig;
        /** Whether the ActorRef should be cluster singleton * */
        public final Boolean isClusterSingleton;
        /** The supervisor strategy to use for this actor */
        @Nullable public final SupervisorStrategy supervisorStrategy;

        /**
         * Creates a new SpawnActor command.
         *
         * @param actorContext The ID of the actor
         * @param replyTo The actor reference to reply to with the spawned actor reference
         * @param mailboxConfig The mailbox configuration
         * @param dispatcherConfig The dispatcher configuration
         * @param tagsConfig The tags configuration
         * @param isClusterSingleton Whether the actor should be cluster singleton
         * @param supervisorStrategy The supervisor strategy (null for no supervision)
         */
        public SpawnActor(
                Class<?> actorClass,
                SpringActorContext actorContext,
                ActorRef<Spawned<?>> replyTo,
                MailboxConfig mailboxConfig,
                DispatcherConfig dispatcherConfig,
                TagsConfig tagsConfig,
                Boolean isClusterSingleton,
                @Nullable SupervisorStrategy supervisorStrategy) {
            this.actorClass = actorClass;
            this.actorContext = actorContext;
            this.replyTo = replyTo;
            this.mailboxConfig = mailboxConfig;
            this.dispatcherConfig = dispatcherConfig;
            this.tagsConfig = tagsConfig;
            this.isClusterSingleton = isClusterSingleton;
            this.supervisorStrategy = supervisorStrategy;
        }
    }

    /**
     * Command to get an existing actor reference by looking it up in the actor context.
     */
    class GetActor implements Command {
        public final Class<?> actorClass;
        public final SpringActorContext actorContext;
        public final ActorRef<GetActorResponse<?>> replyTo;

        public GetActor(Class<?> actorClass, SpringActorContext actorContext, ActorRef<GetActorResponse<?>> replyTo) {
            this.actorClass = actorClass;
            this.actorContext = actorContext;
            this.replyTo = replyTo;
        }
    }

    /**
     * Response containing an actor reference if found, or null if not found.
     *
     * @param <T> The type of messages that the actor can handle
     */
    class GetActorResponse<T> {
        @Nullable public final ActorRef<T> ref;

        public GetActorResponse(@Nullable ActorRef<T> ref) {
            this.ref = ref;
        }
    }

    /**
     * Command to check if an actor exists by looking it up in the actor context.
     */
    class CheckExists implements Command {
        public final Class<?> actorClass;
        public final SpringActorContext actorContext;
        public final ActorRef<ExistsResponse> replyTo;

        public CheckExists(Class<?> actorClass, SpringActorContext actorContext, ActorRef<ExistsResponse> replyTo) {
            this.actorClass = actorClass;
            this.actorContext = actorContext;
            this.replyTo = replyTo;
        }
    }

    /**
     * Response indicating whether an actor exists.
     */
    class ExistsResponse {
        public final boolean exists;

        public ExistsResponse(boolean exists) {
            this.exists = exists;
        }
    }

    /**
     * Response message containing a reference to a spawned actor. This message is sent in response to
     * a SpawnActor command.
     *
     * @param <T> The type of messages that the actor can handle
     */
    class Spawned<T> {
        /** The reference to the spawned actor */
        public final ActorRef<T> ref;

        /**
         * Creates a new Spawned message with the given actor reference.
         *
         * @param ref The reference to the spawned actor
         */
        public Spawned(ActorRef<T> ref) {
            this.ref = ref;
        }
    }

    /**
     * Command to create a new topic.
     */
    class CreateTopic<T> implements Command {
        public final Class<T> messageType;
        public final String topicName;
        public final ActorRef<TopicCreated<T>> replyTo;

        public CreateTopic(Class<T> messageType, String topicName, ActorRef<TopicCreated<T>> replyTo) {
            this.messageType = messageType;
            this.topicName = topicName;
            this.replyTo = replyTo;
        }
    }

    /**
     * Command to get or create a topic (idempotent).
     */
    class GetOrCreateTopic<T> implements Command {
        public final Class<T> messageType;
        public final String topicName;
        public final ActorRef<TopicCreated<T>> replyTo;

        public GetOrCreateTopic(Class<T> messageType, String topicName, ActorRef<TopicCreated<T>> replyTo) {
            this.messageType = messageType;
            this.topicName = topicName;
            this.replyTo = replyTo;
        }
    }

    /**
     * Response message containing a reference to a created topic.
     *
     * @param <T> The type of messages that the topic handles
     */
    class TopicCreated<T> {
        @Nullable public final SpringTopicRef<T> topicRef;
        @Nullable public final String errorMessage;
        public final boolean alreadyExists;

        private TopicCreated(@Nullable SpringTopicRef<T> topicRef, @Nullable String errorMessage, boolean alreadyExists) {
            this.topicRef = topicRef;
            this.errorMessage = errorMessage;
            this.alreadyExists = alreadyExists;
        }

        /**
         * Creates a success response with a topic reference.
         *
         * @param topicRef The created topic reference
         * @param <T> The message type
         * @return A success response
         */
        public static <T> TopicCreated<T> success(SpringTopicRef<T> topicRef) {
            return new TopicCreated<>(topicRef, null, false);
        }

        /**
         * Creates a failure response indicating the topic already exists.
         *
         * @param errorMessage The error message
         * @param <T> The message type
         * @return A failure response
         */
        public static <T> TopicCreated<T> alreadyExists(String errorMessage) {
            return new TopicCreated<>(null, errorMessage, true);
        }

        /**
         * Checks if the topic creation was successful.
         *
         * @return true if successful, false otherwise
         */
        public boolean isSuccess() {
            return topicRef != null && errorMessage == null;
        }
    }

    /**
     * Creates the default RootGuardian behavior using the given actor type registry.
     *
     * @param registry The ActorTypeRegistry to use for creating actor behaviors
     * @return A behavior for the RootGuardian
     */
    static Behavior<Command> create(ActorTypeRegistry registry) {
        return DefaultRootGuardian.create(registry);
    }
}

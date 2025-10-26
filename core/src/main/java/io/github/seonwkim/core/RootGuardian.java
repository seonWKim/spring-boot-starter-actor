package io.github.seonwkim.core;

import io.github.seonwkim.core.impl.DefaultRootGuardian;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.MailboxSelector;

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
        /** The mailbox selector to use * */
        public final MailboxSelector mailboxSelector;
        // TOOD: support for cluster singleton actor creation
        /** Whether the ActorRef should be cluster singleton * */
        public final Boolean isClusterSingleton;

        /**
         * Creates a new SpawnActor command.
         *
         * @param actorContext The ID of the actor
         * @param replyTo The actor reference to reply to with the spawned actor reference
         * @param mailboxSelector The mailboxSelector
         * @param isClusterSingleton Whether the actor should be cluster singleton
         */
        public SpawnActor(
                Class<?> actorClass,
                SpringActorContext actorContext,
                ActorRef<Spawned<?>> replyTo,
                MailboxSelector mailboxSelector,
                Boolean isClusterSingleton) {
            this.actorClass = actorClass;
            this.actorContext = actorContext;
            this.replyTo = replyTo;
            this.mailboxSelector = mailboxSelector;
            this.isClusterSingleton = isClusterSingleton;
        }
    }

    /**
     * Command to get an existing actor reference by looking it up in the actor context.
     */
    class GetActor implements Command {
        public final Class<?> actorClass;
        public final SpringActorContext actorContext;
        public final ActorRef<GetActorResponse<?>> replyTo;

        public GetActor(
                Class<?> actorClass,
                SpringActorContext actorContext,
                ActorRef<GetActorResponse<?>> replyTo) {
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
        public final ActorRef<T> ref;

        public GetActorResponse(ActorRef<T> ref) {
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

        public CheckExists(
                Class<?> actorClass,
                SpringActorContext actorContext,
                ActorRef<ExistsResponse> replyTo) {
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
     * Creates the default RootGuardian behavior using the given actor type registry.
     *
     * @param registry The ActorTypeRegistry to use for creating actor behaviors
     * @return A behavior for the RootGuardian
     */
    static Behavior<Command> create(ActorTypeRegistry registry) {
        return DefaultRootGuardian.create(registry);
    }
}

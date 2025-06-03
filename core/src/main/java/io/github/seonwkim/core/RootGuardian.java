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
	 * Interface for results of stopping an actor. Implementations of this interface are returned in
	 * response to a StopActor command.
	 */
	interface StopResult extends Command {}

	/**
	 * Command to spawn a new actor. This command is sent to the root guardian to create a new actor
	 * of the specified type with the given ID.
	 */
	class SpawnActor<A extends SpringActor<A, C>, C> implements Command {
		public final Class<A> actorClass;
		/** The class of commands that the actor can handle */
		public final Class<C> commandClass;
		/** The context of the actor */
		public final SpringActorContext actorContext;
		/** The actor reference to reply to with the spawned actor reference */
		public final ActorRef<Spawned<C>> replyTo;
		/** The mailbox selector to use * */
		public final MailboxSelector mailboxSelector;
		/** Whether the ActorRef should be cluster singleton * */
		public final Boolean isClusterSingleton;

		/**
		 * Creates a new SpawnActor command.
		 *
		 * @param commandClass The class of commands that the actor can handle
		 * @param actorContext The ID of the actor
		 * @param replyTo The actor reference to reply to with the spawned actor reference
		 * @param mailboxSelector The mailboxSelector
		 * @param isClusterSingleton Whether the actor should be cluster singleton
		 */
		public SpawnActor(
                Class<A> actorClass,
				Class<C> commandClass,
                SpringActorContext actorContext,
                ActorRef<Spawned<C>> replyTo,
                MailboxSelector mailboxSelector,
                Boolean isClusterSingleton) {
            this.actorClass = actorClass;
            this.commandClass = commandClass;
			this.actorContext = actorContext;
			this.replyTo = replyTo;
			this.mailboxSelector = mailboxSelector;
			this.isClusterSingleton = isClusterSingleton;
		}
	}

	/**
	 * Sends a command to stop an existing actor managed by the actor management system.
	 */
	class StopActor implements Command {
		public final Class<?> actorClass;
		/** The context of the actor to be stopped */
		public final SpringActorContext actorContext;
		/** The actor reference to reply to with the stop result */
		public final ActorRef<StopResult> replyTo;

		/**
		 * Creates a new StopActor command.
		 */
		public StopActor(Class<?> actorClass,
						 SpringActorContext actorContext,
						 ActorRef<StopResult> replyTo) {
            this.actorClass = actorClass;
			this.actorContext = actorContext;
			this.replyTo = replyTo;
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
	 * Response message indicating that an actor was successfully stopped. This message is sent in
	 * response to a StopActor command when the actor is found and stopped.
	 */
	class Stopped implements StopResult {}

	/**
	 * Response message indicating that an actor was not found and could not be stopped. This message
	 * is sent in response to a StopActor command when the actor is not found.
	 */
	class ActorNotFound implements StopResult {}

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

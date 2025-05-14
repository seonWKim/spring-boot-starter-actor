package io.github.seonwkim.core.impl;

import io.github.seonwkim.core.ActorTypeRegistry;
import io.github.seonwkim.core.RootGuardian;
import java.util.HashMap;
import java.util.Map;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.MailboxSelector;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.apache.pekko.actor.typed.javadsl.Behaviors;

/**
 * Default implementation of the RootGuardian interface. This class manages the lifecycle of actors
 * and maintains references to them. It handles commands for spawning actors and returns references
 * to them.
 */
public class DefaultRootGuardian implements RootGuardian {

	/**
	 * Command to spawn a new actor. This command is sent to the root guardian to create a new actor
	 * of the specified type with the given ID.
	 *
	 * @param <T> The type of messages that the actor can handle
	 */
	public static class SpawnActor<T> implements Command {
		/** The class of commands that the actor can handle */
		public final Class<T> commandClass;
		/** The ID of the actor */
		public final String actorId;
		/** The actor reference to reply to with the spawned actor reference */
		public final ActorRef<Spawned<T>> replyTo;
		/** The mailbox selector to use * */
		public final MailboxSelector mailboxSelector;

		/**
		 * Creates a new SpawnActor command.
		 *
		 * @param commandClass The class of commands that the actor can handle
		 * @param actorId The ID of the actor
		 * @param replyTo The actor reference to reply to with the spawned actor reference
		 */
		public SpawnActor(
				Class<T> commandClass,
				String actorId,
				ActorRef<Spawned<T>> replyTo,
				MailboxSelector mailboxSelector) {
			this.commandClass = commandClass;
			this.actorId = actorId;
			this.replyTo = replyTo;
			this.mailboxSelector = mailboxSelector;
		}
	}

	/**
	 * Response message containing a reference to a spawned actor. This message is sent in response to
	 * a SpawnActor command.
	 *
	 * @param <T> The type of messages that the actor can handle
	 */
	public static class Spawned<T> {
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
	 * Creates a new DefaultRootGuardian behavior with the given actor type registry.
	 *
	 * @param registry The ActorTypeRegistry to use for creating actor behaviors
	 * @return A behavior for the DefaultRootGuardian
	 */
	public static Behavior<Command> create(ActorTypeRegistry registry) {
		return Behaviors.setup(ctx -> new DefaultRootGuardian(ctx, registry).behavior());
	}

	/** The actor context */
	private final ActorContext<Command> ctx;
	/** The actor type registry */
	private final ActorTypeRegistry registry;
	/** Map of actor references by key */
	private final Map<String, ActorRef<?>> actorRefs = new HashMap<>();

	/**
	 * Creates a new DefaultRootGuardian with the given actor context and actor type registry.
	 *
	 * @param ctx The actor context
	 * @param registry The actor type registry
	 */
	public DefaultRootGuardian(ActorContext<Command> ctx, ActorTypeRegistry registry) {
		this.ctx = ctx;
		this.registry = registry;
	}

	/**
	 * Creates the behavior for this DefaultRootGuardian. The behavior handles SpawnActor commands by
	 * creating or retrieving actor references.
	 *
	 * @return A behavior for this DefaultRootGuardian
	 */
	private Behavior<Command> behavior() {
		return Behaviors.receive(Command.class)
				.onMessage(SpawnActor.class, this::handleSpawnActor)
				.build();
	}

	/**
	 * Handles a SpawnActor command by creating or retrieving an actor reference. If an actor with the
	 * given command class and ID already exists, its reference is returned. Otherwise, a new actor is
	 * created and its reference is returned.
	 *
	 * @param msg The SpawnActor command
	 * @param <T> The type of messages that the actor can handle
	 * @return The same behavior, as this handler doesn't change the behavior
	 */
	@SuppressWarnings("unchecked")
	public <T> Behavior<RootGuardian.Command> handleSpawnActor(SpawnActor<T> msg) {
		String key = buildActorKey(msg.commandClass, msg.actorId);

		ActorRef<T> ref;
		if (actorRefs.containsKey(key)) {
			ref = (ActorRef<T>) actorRefs.get(key);
		} else {
			Behavior<T> behavior = registry.createBehavior(msg.commandClass, msg.actorId);
			ref = ctx.spawn(behavior, key, msg.mailboxSelector);
			actorRefs.put(key, ref);
		}

		msg.replyTo.tell(new Spawned<>(ref));
		return Behaviors.same();
	}

	/**
	 * Builds a unique key for an actor based on its command class and ID.
	 *
	 * @param clazz The command class of the actor
	 * @param actorId The ID of the actor
	 * @return A unique key for the actor
	 */
	private String buildActorKey(Class<?> clazz, String actorId) {
		return clazz.getName() + "-" + actorId;
	}
}

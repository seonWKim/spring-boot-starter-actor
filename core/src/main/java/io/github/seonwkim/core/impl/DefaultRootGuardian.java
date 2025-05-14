package io.github.seonwkim.core.impl;

import io.github.seonwkim.core.ActorTypeRegistry;
import io.github.seonwkim.core.RootGuardian;
import java.util.HashMap;
import java.util.Map;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.apache.pekko.actor.typed.javadsl.Behaviors;

/**
 * Default implementation of the RootGuardian interface. This class manages the lifecycle of actors
 * and maintains references to them. It handles commands for spawning actors and returns references
 * to them.
 */
public class DefaultRootGuardian implements RootGuardian {

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
		return Behaviors.setup(
				ctx ->
						Behaviors.receive(Command.class)
								.onMessage(SpawnActor.class, msg -> handleSpawnActor(msg))
								.onMessage(StopActor.class, msg -> handleStopActor((StopActor<?>) msg))
								.build());
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

	@SuppressWarnings("unchecked")
	public <T> Behavior<RootGuardian.Command> handleStopActor(StopActor<T> msg) {
		String key = buildActorKey(msg.commandClass, msg.actorId);

		final ActorRef<T> actorRef = (ActorRef<T>) actorRefs.get(key);
		if (actorRef != null) {
			actorRefs.remove(key);
			ctx.stop(actorRef);
			msg.replyTo.tell(new Stopped());
		} else {
			msg.replyTo.tell(new ActorNotFound());
		}

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

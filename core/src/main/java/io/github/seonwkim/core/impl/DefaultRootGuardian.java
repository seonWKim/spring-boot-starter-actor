package io.github.seonwkim.core.impl;

import io.github.seonwkim.core.ActorTypeRegistry;
import io.github.seonwkim.core.RootGuardian;
import io.github.seonwkim.core.SpringActor;
import io.github.seonwkim.core.SpringActorContext;

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
								.onMessage(SpawnActor.class, msg -> handleSpawnActor((SpawnActor<?, ?>) msg))
								.onMessage(StopActor.class, msg -> handleStopActor((StopActor<?, ?>) msg))
								.build());
	}

	/**
	 * Handles a SpawnActor command by creating or retrieving an actor reference. If an actor with the
	 * given command class and ID already exists, its reference is returned. Otherwise, a new actor is
	 * created and its reference is returned.
	 *
	 * @param msg The SpawnActor command
	 * @param <C> The type of messages that the actor can handle
	 * @return The same behavior, as this handler doesn't change the behavior
	 */
	@SuppressWarnings("unchecked")
	public <A extends SpringActor<A, C>, C> Behavior<RootGuardian.Command> handleSpawnActor(SpawnActor<A, C> msg) {
		String key = buildActorKey(msg.actorClass, msg.actorContext);

		ActorRef<C> ref;
		if (actorRefs.containsKey(key)) {
			ref = (ActorRef<C>) actorRefs.get(key);
		} else {
			Behavior<C> behavior = registry.createBehavior(msg.actorClass, msg.actorContext);
			ref = ctx.spawn(behavior, key, msg.mailboxSelector);
			actorRefs.put(key, ref);
		}

		msg.replyTo.tell(new Spawned<>(ref));
		return Behaviors.same();
	}

	/**
	 * Handles a StopActor command by stopping an actor and removing its reference. If an actor with
	 * the given command class and ID exists, it is stopped and a Stopped message is sent to the
	 * reply-to actor. Otherwise, an ActorNotFound message is sent.
	 *
	 * @param msg The StopActor command
	 * @return The same behavior, as this handler doesn't change the behavior
	 */
	@SuppressWarnings("unchecked")
	public <A extends SpringActor<A, C>, C> Behavior<RootGuardian.Command> handleStopActor(StopActor<A, C> msg) {
		String key = buildActorKey(msg.actorClass, msg.actorContext);

		final ActorRef<C> actorRef = (ActorRef<C>) actorRefs.get(key);
		if (actorRef != null) {
			actorRefs.remove(key);
			ctx.stop(actorRef);
			msg.replyTo.tell(new Stopped());
		} else {
			msg.replyTo.tell(new ActorNotFound());
		}

		return Behaviors.same();
	}

	private String buildActorKey(Class<?> actorClass, SpringActorContext actorContext) {
		return actorClass.getName() + "-" + actorContext.actorId();
	}
}

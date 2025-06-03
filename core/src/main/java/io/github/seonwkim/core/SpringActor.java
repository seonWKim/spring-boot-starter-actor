package io.github.seonwkim.core;

import org.apache.pekko.actor.typed.Behavior;

/**
 * Interface for Spring-managed actors. Classes implementing this interface will be automatically
 * registered with the actor system.
 *
 * <p>The SpringActor interface is part of the actor system architecture:
 * <ul>
 *   <li>SpringActor: Interface for actor implementations that can be managed by the Spring actor system</li>
 *   <li>SpringActorSystem: Main entry point for creating and managing actors</li>
 *   <li>RootGuardian: Internal component that manages the lifecycle of actors</li>
 * </ul>
 *
 * <p>When you implement this interface, your actor will be registered with the ActorTypeRegistry
 * and can be spawned using the SpringActorSystem.spawn methods.
 *
 * <p>The generic type parameters ensure type safety by enforcing that the actor implementation
 * can only handle commands of the specified type.
 */
public interface SpringActor<A extends SpringActor<A, C>, C> {
	/**
	 * Creates a behavior for this actor. This method is called by the actor system when a new actor
	 * is created.
	 *
	 * @param actorContext The context of the actor
	 * @return A behavior for the actor
	 */
	Behavior<C> create(SpringActorContext actorContext);
}

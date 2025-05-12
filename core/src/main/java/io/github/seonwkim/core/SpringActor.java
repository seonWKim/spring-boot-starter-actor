package io.github.seonwkim.core;

import org.apache.pekko.actor.typed.Behavior;

/**
 * Interface for Spring-managed actors. Classes implementing this interface will be automatically
 * registered with the actor system.
 */
public interface SpringActor {
	/**
	 * Returns the class of commands that this actor can handle. This is used to register the actor
	 * with the ActorTypeRegistry.
	 *
	 * @return The class of commands that this actor can handle
	 */
	Class<?> commandClass();

	/**
	 * Creates a behavior for this actor. This method is called by the actor system when a new actor
	 * is created.
	 *
	 * @param id The ID of the actor
	 * @return A behavior for the actor
	 */
	Behavior<?> create(String id);
}

package io.github.seonwkim.core;

import org.apache.pekko.actor.typed.Behavior;

/**
 * Interface for Spring-managed actors. Classes implementing this interface will be automatically
 * registered with the actor system.
 *
 * The purpose of forcing the return type of the create method to the actor's command class is
 * for simplicity(e.g. Multiple SpringActor's implementing the same class will increase overall complexity).
 */
public interface SpringActor<T> {
	/**
	 * Returns the class of commands that this actor can handle. This is used to register the actor
	 * with the ActorTypeRegistry.
	 *
	 * @return The class of commands that this actor can handle
	 */
	Class<T> commandClass();

	/**
	 * Creates a behavior for this actor. This method is called by the actor system when a new actor
	 * is created.
	 *
	 * @param actorContext The context of the actor
	 * @return A behavior for the actor
	 */
	Behavior<T> create(SpringActorContext actorContext);
}

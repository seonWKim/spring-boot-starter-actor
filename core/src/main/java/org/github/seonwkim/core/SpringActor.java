package org.github.seonwkim.core;

/**
 * Interface for Spring-managed actors.
 * Classes implementing this interface will be automatically registered with the actor system.
 * Each SpringActor must have a static create(String) method that returns a Behavior.
 */
public interface SpringActor {
    /**
     * Returns the class of commands that this actor can handle.
     * This is used to register the actor with the ActorTypeRegistry.
     *
     * @return The class of commands that this actor can handle
     */
    Class<?> commandClass();
}

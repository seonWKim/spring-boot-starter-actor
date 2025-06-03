package io.github.seonwkim.core;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import org.apache.pekko.actor.typed.Behavior;

/**
 * Registry for actor types that allows registering actor factories and creating actor behaviors.
 * This class maintains a mapping between command classes and their corresponding actor behavior
 * factories.
 */
public class ActorTypeRegistry {

	private final Map<Class<?>, Function<SpringActorContext, Behavior<?>>> classToFactory = new HashMap<>();

	/**
	 * Registers a factory for creating actor behaviors for the given actor class.
	 *
	 * @param actorClass The actor class to register
	 * @param factory The factory for creating actor behaviors
	 */
	public void register(Class<?> actorClass, Function<SpringActorContext, Behavior<?>> factory) {
		classToFactory.put(actorClass, factory);
	}

	/**
	 * Creates a behavior for the given actor class and context.
	 *
	 * @param actorClass The actor class to create a behavior for
	 * @param actorContext The context to use for creating the behavior
	 * @return A behavior for the given actor class and context
	 * @throws IllegalArgumentException If no factory is registered for the given actor class
	 */
	public Behavior<?> createBehavior(Class<?> actorClass, SpringActorContext actorContext) {
		final Function<SpringActorContext, Behavior<?>> factory = classToFactory.get(actorClass);
		if (factory == null) {
			throw new IllegalArgumentException("No factory registered for class: " + actorClass.getName());
		}

		return factory.apply(actorContext);
	}
}

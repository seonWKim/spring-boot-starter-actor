package io.github.seonwkim.core;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import org.apache.pekko.actor.typed.Behavior;

/**
 * Registry for actor types that allows registering actor factories and creating actor behaviors.
 * This class maintains a mapping between actor classes and their corresponding actor behavior
 * factories.
 */
public class ActorTypeRegistry {

	private final Map<Class<?>, Function<SpringActorContext, Behavior<?>>> classToFactory = new HashMap<>();

	/**
	 * Registers a factory for creating actor behaviors for the given actor class.
	 * The factory produces behaviors for actors that handle commands of type C.
	 *
	 * @param actorClass The actor class to register
	 * @param factory The factory for creating actor behaviors
	 * @param <A> The type of the actor (must extend SpringActor)
	 * @param <C> The type of commands the actor handles
	 */
	public <A extends SpringActor<A, C>, C> void register(
			Class<A> actorClass, 
			Function<SpringActorContext, Behavior<C>> factory) {
		// Safe cast due to type erasure - the generic types ensure compile-time safety
		@SuppressWarnings("unchecked")
		Function<SpringActorContext, Behavior<?>> erasedFactory = 
			(Function<SpringActorContext, Behavior<?>>) (Function<?, ?>) factory;
		classToFactory.put(actorClass, erasedFactory);
	}

	/**
	 * Creates a behavior for the given actor class and context.
	 * This method is used internally when the exact type parameters are unknown at compile time.
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

	/**
	 * Creates a behavior for the given actor class and context with type safety.
	 * This method provides compile-time type checking for registered actors.
	 *
	 * @param actorClass The actor class to create a behavior for
	 * @param actorContext The context to use for creating the behavior
	 * @param <A> The type of the actor (must extend SpringActor)
	 * @param <C> The type of commands the actor handles
	 * @return A behavior for the given actor class and context
	 * @throws IllegalArgumentException If no factory is registered for the given actor class
	 */
	@SuppressWarnings("unchecked")
	public <A extends SpringActor<A, C>, C> Behavior<C> createTypedBehavior(
			Class<A> actorClass, 
			SpringActorContext actorContext) {
		// Delegate to the non-generic method and cast the result
		// Safe cast - the register method ensures type consistency
		return (Behavior<C>) createBehavior(actorClass, actorContext);
	}
}

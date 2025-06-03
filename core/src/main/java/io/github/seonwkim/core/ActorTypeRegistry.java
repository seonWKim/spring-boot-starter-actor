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

	public void register(Class<?> actorclass, Function<SpringActorContext, Behavior<?>> factory) {
		classToFactory.put(actorclass, factory);
	}

	@SuppressWarnings("unchecked")
	public <A extends SpringActor<A, C>, C> Behavior<C> createBehavior(Class<A> actorClass, SpringActorContext actorContext) {
		final Function<SpringActorContext, Behavior<?>> factory = classToFactory.get(actorClass);
		if (factory == null) {
			throw new IllegalArgumentException("No factory registered for class: " + actorClass.getName());
		}

		return (Behavior<C>) factory.apply(actorContext);
	}
}

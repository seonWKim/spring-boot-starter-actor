package io.github.seonwkim.core;

import org.apache.pekko.actor.typed.Behavior;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Registry for actor types that allows registering actor factories and creating actor behaviors.
 * This class maintains a mapping between command classes and their corresponding actor behavior factories.
 */
public class ActorTypeRegistry {

    private final Map<String, Function<String, Behavior<?>>> factories = new HashMap<>();
    private final Map<Class<?>, String> classToKey = new HashMap<>();

    /**
     * Registers a factory function for creating actor behaviors for a specific command class.
     *
     * @param commandClass The class of commands that the actor can handle
     * @param factory The factory function that creates a behavior for the actor given an ID
     * @param <T> The type of commands that the actor can handle
     */
    @SuppressWarnings("unchecked")
    public <T> void register(Class<T> commandClass, Function<String, Behavior<?>> factory) {
        String key = commandClass.getName();
        Function<String, Behavior<?>> castedFactory = factory;

        factories.put(key, castedFactory);
        classToKey.put(commandClass, key);
    }

    /**
     * Creates a behavior for a given command class and ID.
     *
     * @param commandClass The class of commands that the actor can handle
     * @param id The ID of the actor
     * @param <T> The type of commands that the actor can handle
     * @return A behavior for the actor
     * @throws IllegalArgumentException If no factory is registered for the command class
     */
    @SuppressWarnings("unchecked")
    public <T> Behavior<T> createBehavior(Class<T> commandClass, String id) {
        String key = classToKey.get(commandClass);
        if (key == null) {
            throw new IllegalArgumentException("No factory registered for class: " + commandClass.getName());
        }
        return (Behavior<T>) createBehaviorByKey(key, id);
    }

    private Behavior<?> createBehaviorByKey(String key, String id) {
        Function<String, Behavior<?>> factory = factories.get(key);
        if (factory == null) {
            throw new IllegalArgumentException("No factory registered for key: " + key);
        }
        return factory.apply(id);
    }
}

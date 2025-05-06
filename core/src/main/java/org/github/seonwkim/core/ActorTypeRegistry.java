package org.github.seonwkim.core;

import org.apache.pekko.actor.typed.Behavior;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

// TODO: fix name, ActorTypeRegistry or ActorRegistry
public class ActorTypeRegistry {

    private final Map<String, Function<String, Behavior<?>>> factories = new HashMap<>();
    private final Map<Class<?>, String> classToKey = new HashMap<>();

    @SuppressWarnings("unchecked")
    public <T> void register(Class<T> commandClass, Function<String, Behavior<?>> factory) {
        String key = commandClass.getName();
        Function<String, Behavior<?>> castedFactory = factory;

        factories.put(key, castedFactory);
        classToKey.put(commandClass, key);
    }

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

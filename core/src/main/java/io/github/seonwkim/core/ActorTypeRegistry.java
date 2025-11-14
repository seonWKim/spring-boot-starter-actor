package io.github.seonwkim.core;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;

/**
 * Static registry for actor types. This class maintains thread-safe mappings between actor classes
 * and their corresponding behavior factories. All methods are static and use concurrent maps for thread-safety.
 *
 * <p>This is a pure utility class - not managed by Spring. Actors self-register via @PostConstruct.
 */
public final class ActorTypeRegistry {

    private static final ConcurrentMap<Class<?>, Function<SpringActorContext, SpringActorBehavior<?>>> classToFactory =
            new ConcurrentHashMap<>();

    // Prevent instantiation
    private ActorTypeRegistry() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Registers a factory for creating actor behaviors for the given actor class. The factory
     * produces behaviors for actors that handle commands of type C. Thread-safe.
     *
     * @param actorClass The actor class to register
     * @param factory The factory for creating actor behaviors
     * @param <C> The type of commands the actor handles
     * @param <CTX> The type of context the actor requires
     */
    public static <C, CTX extends SpringActorContext> void register(
            Class<? extends SpringActorWithContext<C, CTX>> actorClass,
            Function<SpringActorContext, SpringActorBehavior<C>> factory) {
        // Safe cast due to type erasure - the generic types ensure compile-time safety
        @SuppressWarnings("unchecked")
        Function<SpringActorContext, SpringActorBehavior<?>> erasedFactory =
                (Function<SpringActorContext, SpringActorBehavior<?>>) (Function<?, ?>) factory;
        registerInternal(actorClass, erasedFactory);
    }

    /**
     * Internal method for registering actors with raw types. Used by the framework when
     * dealing with Spring beans where exact types are unknown at compile time. Thread-safe.
     *
     * @param actorClass The actor class to register
     * @param factory The factory for creating actor behaviors
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    static void registerInternal(Class<?> actorClass, Function<SpringActorContext, SpringActorBehavior<?>> factory) {
        classToFactory.put(actorClass, factory);
    }

    /**
     * Creates a behavior for the given actor class and context with type safety. This method provides
     * compile-time type checking for registered actors. Thread-safe.
     */
    @SuppressWarnings("unchecked")
    public static <C, CTX extends SpringActorContext> SpringActorBehavior<C> createTypedBehavior(
            Class<? extends SpringActorWithContext<C, CTX>> actorClass, SpringActorContext actorContext) {
        // Safe cast: register method ensures type consistency
        return (SpringActorBehavior<C>) createBehavior(actorClass, actorContext);
    }

    /**
     * Creates a behavior for the given actor class and context. This method is used internally when
     * the exact type parameters are unknown at compile time. Thread-safe.
     *
     * @param actorClass The actor class to create a behavior for
     * @param actorContext The context to use for creating the behavior
     * @return A SpringActorBehavior for the given actor class and context
     * @throws IllegalArgumentException If no factory is registered for the given actor class
     */
    public static SpringActorBehavior<?> createBehavior(Class<?> actorClass, SpringActorContext actorContext) {
        final Function<SpringActorContext, SpringActorBehavior<?>> factory = classToFactory.get(actorClass);
        if (factory == null) {
            throw new IllegalArgumentException("No factory registered for class: " + actorClass.getName());
        }

        return factory.apply(actorContext);
    }

    /**
     * Clears all registrations. Primarily for testing.
     */
    public static void clear() {
        classToFactory.clear();
    }
}

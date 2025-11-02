package io.github.seonwkim.core;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Registry for actor types that allows registering actor factories and creating actor behaviors.
 * This class maintains a mapping between actor classes and their corresponding actor behavior
 * factories.
 */
public class ActorTypeRegistry {

    private final Map<Class<?>, Function<SpringActorContext, SpringActorBehavior<?>>> classToFactory = new HashMap<>();

    /**
     * Registers a factory for creating actor behaviors for the given actor class. The factory
     * produces behaviors for actors that handle commands of type C.
     *
     * @param actorClass The actor class to register
     * @param factory The factory for creating actor behaviors
     * @param <C> The type of commands the actor handles
     * @param <CTX> The type of context the actor requires
     */
    public <C, CTX extends SpringActorContext> void register(
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
     * dealing with Spring beans where exact types are unknown at compile time.
     *
     * @param actorClass The actor class to register
     * @param factory The factory for creating actor behaviors
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    void registerInternal(Class<?> actorClass, Function<SpringActorContext, SpringActorBehavior<?>> factory) {
        classToFactory.put(actorClass, factory);
    }

    /**
     * Creates a behavior for the given actor class and context with type safety. This method provides
     * compile-time type checking for registered actors.
     */
    @SuppressWarnings("unchecked")
    public <C, CTX extends SpringActorContext> SpringActorBehavior<C> createTypedBehavior(
            Class<? extends SpringActorWithContext<C, CTX>> actorClass, SpringActorContext actorContext) {
        // Safe cast: register method ensures type consistency
        return (SpringActorBehavior<C>) createBehavior(actorClass, actorContext);
    }

    /**
     * Creates a behavior for the given actor class and context. This method is used internally when
     * the exact type parameters are unknown at compile time.
     *
     * <p>If the provided context has a null registry, this method automatically wraps the context
     * to inject this registry, enabling hierarchical supervision support.
     *
     * @param actorClass The actor class to create a behavior for
     * @param actorContext The context to use for creating the behavior
     * @return A SpringActorBehavior for the given actor class and context
     * @throws IllegalArgumentException If no factory is registered for the given actor class
     */
    public SpringActorBehavior<?> createBehavior(Class<?> actorClass, SpringActorContext actorContext) {
        final Function<SpringActorContext, SpringActorBehavior<?>> factory = classToFactory.get(actorClass);
        if (factory == null) {
            throw new IllegalArgumentException("No factory registered for class: " + actorClass.getName());
        }

        // Inject registry if the context doesn't have one
        SpringActorContext contextWithRegistry =
                actorContext.registry() == null ? wrapContextWithRegistry(actorContext) : actorContext;

        return factory.apply(contextWithRegistry);
    }

    /**
     * Wraps a context to inject this registry, enabling hierarchical supervision.
     *
     * @param originalContext The original context
     * @return A wrapped context with this registry injected
     */
    private SpringActorContext wrapContextWithRegistry(SpringActorContext originalContext) {
        return new SpringActorContext() {
            @Override
            public String actorId() {
                return originalContext.actorId();
            }

            @Override
            public ActorTypeRegistry registry() {
                return ActorTypeRegistry.this;
            }
        };
    }
}

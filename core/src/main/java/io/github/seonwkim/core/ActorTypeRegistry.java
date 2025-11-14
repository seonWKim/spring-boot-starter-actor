package io.github.seonwkim.core;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;

/**
 * Static registry for actor types. Maintains thread-safe mappings between actor classes
 * and their behavior factories. All operations are thread-safe via {@link ConcurrentHashMap}.
 *
 * <p>Pure utility class - not managed by Spring. Actors are automatically registered by
 * {@link ActorConfiguration#actorRegistrationBeanPostProcessor()}.
 */
public final class ActorTypeRegistry {

    private static final ConcurrentMap<Class<?>, Function<SpringActorContext, SpringActorBehavior<?>>> classToFactory =
            new ConcurrentHashMap<>();

    // Prevent instantiation
    private ActorTypeRegistry() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Registers an actor behavior factory.
     *
     * <p><b>Note:</b> In most cases, you don't need to call this method directly.
     * Actors implementing {@link SpringActor} or {@link SpringActorWithContext} and
     * annotated with {@code @Component} are automatically registered by the framework.
     *
     * <p>Manual registration may be useful for:
     * <ul>
     *   <li>Registering actors programmatically outside of Spring's component scanning</li>
     *   <li>Advanced testing scenarios where you need fine-grained control</li>
     *   <li>Dynamic actor types generated at runtime</li>
     * </ul>
     *
     * @param <C> The command type that the actor handles
     * @param <CTX> The context type that the actor requires
     * @param actorClass Actor class to register
     * @param factory Factory for creating actor behaviors
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
     * Internal registration method used by {@link ActorConfiguration}.
     * Not for application use.
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    static void registerInternal(Class<?> actorClass, Function<SpringActorContext, SpringActorBehavior<?>> factory) {
        classToFactory.put(actorClass, factory);
    }

    /**
     * Creates a behavior with compile-time type checking.
     *
     * @param <C> The command type that the actor handles
     * @param <CTX> The context type that the actor requires
     * @param actorClass Actor class to create behavior for
     * @param actorContext Context for creating the behavior
     * @return Actor behavior instance with proper type information
     */
    @SuppressWarnings("unchecked")
    public static <C, CTX extends SpringActorContext> SpringActorBehavior<C> createTypedBehavior(
            Class<? extends SpringActorWithContext<C, CTX>> actorClass, SpringActorContext actorContext) {
        // Safe cast: register method ensures type consistency
        return (SpringActorBehavior<C>) createBehavior(actorClass, actorContext);
    }

    /**
     * Creates a behavior for the given actor class and context.
     *
     * @param actorClass Actor class to create behavior for
     * @param actorContext Context for creating the behavior
     * @return Actor behavior instance
     * @throws IllegalArgumentException if no factory is registered for the actor class
     */
    public static SpringActorBehavior<?> createBehavior(Class<?> actorClass, SpringActorContext actorContext) {
        final Function<SpringActorContext, SpringActorBehavior<?>> factory = classToFactory.get(actorClass);
        if (factory == null) {
            throw new IllegalArgumentException("No factory registered for class: " + actorClass.getName());
        }

        return factory.apply(actorContext);
    }

    /**
     * Clears all registrations. <b>For testing use only.</b>
     *
     * <p>This method is provided to ensure test isolation by clearing the static registry
     * between test runs. It should not be used in production code.
     *
     * <p>Typical usage in tests:
     * <pre>{@code
     * @BeforeEach
     * public void setUp() {
     *     ActorTypeRegistry.clear();
     * }
     * }</pre>
     */
    public static void clear() {
        classToFactory.clear();
    }
}

package io.github.seonwkim.actor.internal;

import io.github.seonwkim.actor.SpringActorBehavior;
import io.github.seonwkim.actor.SpringActorContext;
import io.github.seonwkim.actor.SpringActorWithContext;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;
import org.apache.pekko.actor.setup.Setup;
import org.apache.pekko.actor.typed.ActorSystem;

/**
 * The behavior factories of one actor system, by actor class. Each Spring context has its own
 * (filled by {@code ActorConfiguration}'s bean post-processor), and its actor system carries it as
 * a {@link Setup}, so two contexts in one JVM never see each other's actors.
 */
public final class ActorTypeRegistry extends Setup {

    private final ConcurrentMap<Class<?>, Function<SpringActorContext, SpringActorBehavior<?>>> classToFactory =
            new ConcurrentHashMap<>();

    /** The registry of the given actor system; empty if the system was built without one. */
    public static ActorTypeRegistry of(ActorSystem<?> system) {
        return system.settings().setup().get(ActorTypeRegistry.class).orElseGet(ActorTypeRegistry::new);
    }

    public <C, CTX extends SpringActorContext> void register(
            Class<? extends SpringActorWithContext<C, CTX>> actorClass,
            Function<SpringActorContext, SpringActorBehavior<C>> factory) {
        // Safe cast due to type erasure - the generic types ensure compile-time safety
        @SuppressWarnings("unchecked")
        Function<SpringActorContext, SpringActorBehavior<?>> erasedFactory =
                (Function<SpringActorContext, SpringActorBehavior<?>>) (Function<?, ?>) factory;
        registerInternal(actorClass, erasedFactory);
    }

    public void registerInternal(Class<?> actorClass, Function<SpringActorContext, SpringActorBehavior<?>> factory) {
        classToFactory.put(actorClass, factory);
    }

    @SuppressWarnings("unchecked")
    public <C, CTX extends SpringActorContext> SpringActorBehavior<C> createTypedBehavior(
            Class<? extends SpringActorWithContext<C, CTX>> actorClass, SpringActorContext actorContext) {
        // Safe cast: register method ensures type consistency
        return (SpringActorBehavior<C>) createBehavior(actorClass, actorContext);
    }

    public SpringActorBehavior<?> createBehavior(Class<?> actorClass, SpringActorContext actorContext) {
        final Function<SpringActorContext, SpringActorBehavior<?>> factory = classToFactory.get(actorClass);
        if (factory == null) {
            throw new IllegalArgumentException("No factory registered for class: " + actorClass.getName());
        }
        return factory.apply(actorContext);
    }
}

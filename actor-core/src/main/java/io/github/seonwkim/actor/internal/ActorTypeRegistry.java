package io.github.seonwkim.actor.internal;

import io.github.seonwkim.actor.SpringActorBehavior;
import io.github.seonwkim.actor.SpringActorContext;
import io.github.seonwkim.actor.SpringActorWithContext;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;
import javax.annotation.Nullable;
import org.apache.pekko.actor.setup.Setup;
import org.apache.pekko.actor.typed.ActorSystem;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.util.ClassUtils;

/**
 * The behavior factories of one actor system, by actor class. Each Spring context has its own
 * (filled by {@code ActorConfiguration}'s bean post-processor), and its actor system carries it as
 * a {@link Setup}, so two contexts in one JVM never see each other's actors.
 *
 * <p>An actor class whose bean Spring hasn't created yet (a bean that spawns in its constructor can
 * run before the actor's bean exists) is looked up in the bean factory on first use, which creates it.
 */
public final class ActorTypeRegistry extends Setup {

    private final ConcurrentMap<Class<?>, Function<SpringActorContext, SpringActorBehavior<?>>> classToFactory =
            new ConcurrentHashMap<>();

    @Nullable private final BeanFactory beanFactory;

    /** A registry that knows only the actors registered on it. */
    public ActorTypeRegistry() {
        this(null);
    }

    /** A registry that also finds actor beans of {@code beanFactory} that are not registered yet. */
    public ActorTypeRegistry(@Nullable BeanFactory beanFactory) {
        this.beanFactory = beanFactory;
    }

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

    /**
     * Registers an actor bean. A proxied bean (for example one with {@code @Transactional}) is
     * registered under the class the user declared, not the generated proxy class, so spawning by
     * that class works.
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public void registerBean(SpringActorWithContext actor) {
        Class<?> actorClass = ClassUtils.getUserClass(actor);
        registerInternal(actorClass, actorContext -> {
            try {
                return actor.create(actorContext);
            } catch (Exception e) {
                throw new RuntimeException("Failed to create actor behavior for " + actorClass.getName(), e);
            }
        });
    }

    /**
     * Makes sure {@code actorClass} has a factory: if it has none yet, gets its bean from the bean
     * factory, which creates the bean when Spring hasn't. Call it on the thread that spawns, so a
     * spawn from a bean constructor creates the actor bean on Spring's own startup thread.
     */
    public void resolve(Class<?> actorClass) {
        if (beanFactory == null || classToFactory.containsKey(actorClass)) {
            return;
        }
        Object bean = beanFactory.getBeanProvider(actorClass).getIfUnique();
        if (bean instanceof SpringActorWithContext<?, ?> actor) {
            registerBean(actor);
        }
    }

    @SuppressWarnings("unchecked")
    public <C, CTX extends SpringActorContext> SpringActorBehavior<C> createTypedBehavior(
            Class<? extends SpringActorWithContext<C, CTX>> actorClass, SpringActorContext actorContext) {
        // Safe cast: register method ensures type consistency
        return (SpringActorBehavior<C>) createBehavior(actorClass, actorContext);
    }

    public SpringActorBehavior<?> createBehavior(Class<?> actorClass, SpringActorContext actorContext) {
        resolve(actorClass);
        final Function<SpringActorContext, SpringActorBehavior<?>> factory = classToFactory.get(actorClass);
        if (factory == null) {
            throw new IllegalArgumentException("No factory registered for class: " + actorClass.getName()
                    + ". An actor must be a Spring bean (for example annotated with @Component) in the"
                    + " application context that owns the actor system.");
        }
        return factory.apply(actorContext);
    }
}

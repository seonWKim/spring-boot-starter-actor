package io.github.seonwkim.actor.internal;

import io.github.seonwkim.actor.DispatcherConfig;
import io.github.seonwkim.actor.MailboxConfig;
import io.github.seonwkim.actor.SpringActorBehavior;
import io.github.seonwkim.actor.SpringActorContext;
import io.github.seonwkim.actor.SpringActorWithContext;
import io.github.seonwkim.actor.SupervisionStrategy;
import io.github.seonwkim.actor.TagsConfig;
import javax.annotation.Nullable;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.springframework.core.ResolvableType;

/**
 * Utility class that provides centralized actor spawning logic.
 * This class consolidates the common spawning logic used by both top-level actors
 * (via RootGuardian) and child actors (via SpringActorBehavior).
 *
 * <p>By centralizing this logic, we ensure consistency and reduce code duplication
 * across the framework.
 */
public final class ActorSpawner {

    private ActorSpawner() {
        // Utility class - prevent instantiation
    }

    /**
     * Builds a unique actor name from the actor class and ID.
     * This naming convention is used consistently across the framework to ensure
     * unique identification of actors.
     *
     * @param actorClass The actor class
     * @param actorId The actor ID
     * @return A unique actor name in the format "fully.qualified.ClassName:actorId"
     */
    public static String buildActorName(Class<?> actorClass, String actorId) {
        return actorClass.getName() + ":" + actorId;
    }

    /**
     * The actor id inside a name built by {@link #buildActorName}; the whole name for an actor named
     * otherwise (a sharded entity is named by its entity id).
     */
    public static String actorIdFromName(String actorName) {
        int separator = actorName.indexOf(':');
        return separator < 0 ? actorName : actorName.substring(separator + 1);
    }

    /**
     * Fails when {@code actorClass} takes a custom context type, which a spawn by id alone can't build.
     *
     * @return null when a {@link DefaultSpringActorContext} fits, otherwise the error to report
     */
    @Nullable public static IllegalArgumentException defaultContextMismatch(Class<?> actorClass) {
        Class<?> contextType = ResolvableType.forClass(actorClass)
                .as(SpringActorWithContext.class)
                .getGeneric(1)
                .resolve();
        if (contextType == null || contextType.isAssignableFrom(DefaultSpringActorContext.class)) {
            return null;
        }
        return new IllegalArgumentException(actorClass.getName() + " takes a " + contextType.getName()
                + ", which can't be built from an id alone. Spawn it with withContext(...) on the builder "
                + "instead of withId(...) or getOrSpawn(Class, id).");
    }

    /**
     * Gets a reference to an existing actor (child) in the given context.
     * Returns null if the actor does not exist.
     *
     * @param ctx The actor context to search in
     * @param actorClass The class of the actor to find
     * @param actorId The ID of the actor to find
     * @param <T> The command type of the actor
     * @return The actor reference if found, null otherwise
     */
    @Nullable public static <T> ActorRef<T> getActor(ActorContext<?> ctx, Class<?> actorClass, String actorId) {
        String actorName = buildActorName(actorClass, actorId);

        @SuppressWarnings("unchecked")
        ActorRef<T> ref = (ActorRef<T>) ctx.getChild(actorName).orElse(null);
        return ref;
    }

    /**
     * Checks if an actor exists in the given context.
     *
     * @param ctx The actor context to search in
     * @param actorClass The class of the actor to check
     * @param actorId The ID of the actor to check
     * @return true if the actor exists, false otherwise
     */
    public static boolean actorExists(ActorContext<?> ctx, Class<?> actorClass, String actorId) {
        String actorName = buildActorName(actorClass, actorId);
        return ctx.getChild(actorName).isPresent();
    }

    /**
     * Spawns an actor with the given configuration.
     * This method handles all the common spawning logic including:
     * <ul>
     *   <li>Creating behavior from the static actor type registry</li>
     *   <li>Applying supervision strategy</li>
     *   <li>Configuring mailbox and dispatcher</li>
     *   <li>Applying actor tags for logging/categorization</li>
     *   <li>Supporting cluster singleton spawning</li>
     * </ul>
     *
     * @param ctx The actor context to spawn in
     * @param actorClass The class of the actor to spawn
     * @param actorContext The Spring actor context
     * @param actorName The unique name for the actor
     * @param supervisorStrategy The supervision strategy (null for no supervision)
     * @param mailboxConfig The mailbox configuration
     * @param dispatcherConfig The dispatcher configuration
     * @param tagsConfig The tags configuration for logging/categorization
     * @param isClusterSingleton Whether to spawn as a cluster singleton
     * @param <T> The command type of the spawned actor
     * @return The reference to the spawned actor
     * @throws IllegalStateException if cluster singleton is requested but not available
     */
    public static <T> ActorRef<T> spawnActor(
            ActorContext<?> ctx,
            Class<?> actorClass,
            SpringActorContext actorContext,
            String actorName,
            @Nullable SupervisionStrategy supervisorStrategy,
            MailboxConfig mailboxConfig,
            DispatcherConfig dispatcherConfig,
            TagsConfig tagsConfig,
            boolean isClusterSingleton) {

        // The behavior comes from this actor system's registry (its Spring context's actor beans)
        SpringActorBehavior<?> defined = ActorTypeRegistry.of(ctx.getSystem()).createBehavior(actorClass, actorContext);
        Behavior<?> behavior = PekkoBehaviors.forNewActor(defined, actorClass, ctx.getSystem());
        if (defined.kind() != SpringActorBehavior.Kind.ROUTER) {
            // A router passes traced messages on to its workers, which receive them.
            behavior = MessageTracingSetup.receiving(ctx.getSystem(), behavior);
        }

        if (supervisorStrategy != null) {
            behavior = Behaviors.supervise(behavior).onFailure(PekkoProps.supervisor(supervisorStrategy));
        }

        ActorRef<?> ref;

        if (isClusterSingleton) {
            ref = ClusterSupport.require()
                    .spawnSingleton(
                            ctx.getSystem(),
                            behavior,
                            actorName,
                            PekkoProps.of(dispatcherConfig, mailboxConfig, tagsConfig));
        } else {
            ref = ctx.spawn(behavior, actorName, PekkoProps.of(dispatcherConfig, mailboxConfig, tagsConfig));
        }

        @SuppressWarnings("unchecked")
        ActorRef<T> typedRef = (ActorRef<T>) ref;
        return typedRef;
    }
}

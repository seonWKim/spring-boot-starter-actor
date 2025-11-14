package io.github.seonwkim.core;

import javax.annotation.Nullable;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.Props;
import org.apache.pekko.actor.typed.SupervisorStrategy;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.cluster.typed.ClusterSingleton;
import org.apache.pekko.cluster.typed.SingletonActor;

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
     * @param clusterSingleton The cluster singleton (null if not in cluster mode)
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
            @Nullable SupervisorStrategy supervisorStrategy,
            MailboxConfig mailboxConfig,
            DispatcherConfig dispatcherConfig,
            TagsConfig tagsConfig,
            @Nullable ClusterSingleton clusterSingleton,
            boolean isClusterSingleton) {

        // Create behavior from static registry using Spring DI
        Behavior<?> behavior =
                ActorTypeRegistry.createBehavior(actorClass, actorContext).asBehavior();

        if (supervisorStrategy != null) {
            behavior = Behaviors.supervise(behavior).onFailure(supervisorStrategy);
        }

        ActorRef<?> ref;

        if (isClusterSingleton) {
            if (clusterSingleton == null) {
                throw new IllegalStateException("Cluster singleton requested but cluster mode is not enabled. "
                        + "Ensure your application is running in cluster mode.");
            }

            SingletonActor<?> singletonActor = SingletonActor.of(behavior, actorName);

            // Build Props with dispatcher, mailbox, and tags
            Props props = buildProps(dispatcherConfig, mailboxConfig, tagsConfig);
            if (props != Props.empty()) {
                singletonActor = singletonActor.withProps(props);
            }

            ref = clusterSingleton.init(singletonActor);
        } else {
            // Build Props with dispatcher, mailbox, and tags
            Props props = buildProps(dispatcherConfig, mailboxConfig, tagsConfig);

            if (props != Props.empty()) {
                ref = ctx.spawn(behavior, actorName, props);
            } else {
                // If no props needed, use mailbox selector (more efficient)
                ref = ctx.spawn(behavior, actorName, mailboxConfig.toMailboxSelector());
            }
        }

        @SuppressWarnings("unchecked")
        ActorRef<T> typedRef = (ActorRef<T>) ref;
        return typedRef;
    }

    /**
     * Builds Props by combining dispatcher, mailbox, and tags configurations.
     * Returns Props.empty() if no configuration is needed.
     *
     * <p>Props are combined using the {@code withNext()} method, allowing multiple
     * configurations to be applied together (e.g., dispatcher + mailbox + tags).
     *
     * @param dispatcherConfig The dispatcher configuration
     * @param mailboxConfig The mailbox configuration
     * @param tagsConfig The tags configuration
     * @return Combined Props or Props.empty()
     */
    private static Props buildProps(
            DispatcherConfig dispatcherConfig, MailboxConfig mailboxConfig, TagsConfig tagsConfig) {

        // Start with either dispatcher props or empty props
        Props props = dispatcherConfig.shouldUseProps() ? dispatcherConfig.toProps() : Props.empty();

        // Apply mailbox configuration
        props = mailboxConfig.applyToProps(props);

        // Apply tags configuration
        props = tagsConfig.applyToProps(props);

        return props;
    }
}

package io.github.seonwkim.core;

import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.Props;
import org.apache.pekko.actor.typed.SupervisorStrategy;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.cluster.typed.ClusterSingleton;
import org.apache.pekko.cluster.typed.SingletonActor;
import javax.annotation.Nullable;

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
     * Spawns an actor with the given configuration.
     * This method handles all the common spawning logic including:
     * <ul>
     *   <li>Creating behavior from the actor type registry</li>
     *   <li>Applying supervision strategy</li>
     *   <li>Configuring mailbox and dispatcher</li>
     *   <li>Supporting cluster singleton spawning</li>
     * </ul>
     *
     * @param ctx The actor context to spawn in
     * @param registry The actor type registry for creating behaviors
     * @param actorClass The class of the actor to spawn
     * @param actorContext The Spring actor context
     * @param actorName The unique name for the actor
     * @param supervisorStrategy The supervision strategy (null for no supervision)
     * @param mailboxConfig The mailbox configuration
     * @param dispatcherConfig The dispatcher configuration
     * @param clusterSingleton The cluster singleton (null if not in cluster mode)
     * @param isClusterSingleton Whether to spawn as a cluster singleton
     * @param <T> The command type of the spawned actor
     * @return The reference to the spawned actor
     * @throws IllegalStateException if cluster singleton is requested but not available
     */
    public static <T> ActorRef<T> spawnActor(
            ActorContext<?> ctx,
            ActorTypeRegistry registry,
            Class<?> actorClass,
            SpringActorContext actorContext,
            String actorName,
            @Nullable SupervisorStrategy supervisorStrategy,
            MailboxConfig mailboxConfig,
            DispatcherConfig dispatcherConfig,
            @Nullable ClusterSingleton clusterSingleton,
            boolean isClusterSingleton) {

        // Create behavior from registry using Spring DI
        Behavior<?> behavior = registry.createBehavior(actorClass, actorContext).asBehavior();

        // Apply supervision strategy if provided
        if (supervisorStrategy != null) {
            behavior = Behaviors.supervise(behavior).onFailure(supervisorStrategy);
        }

        ActorRef<?> ref;

        if (isClusterSingleton) {
            // Spawn as cluster singleton using Pekko's ClusterSingleton API
            if (clusterSingleton == null) {
                throw new IllegalStateException(
                    "Cluster singleton requested but cluster mode is not enabled. " +
                    "Ensure your application is running in cluster mode."
                );
            }

            // Create SingletonActor with the behavior
            // The singleton name should be unique across the cluster
            SingletonActor<?> singletonActor = SingletonActor.of(behavior, actorName);

            // Initialize the singleton and get the proxy reference
            // The proxy transparently routes messages to whichever node hosts the singleton
            ref = clusterSingleton.init(singletonActor);
        } else {
            // Regular actor spawn with mailbox/dispatcher configuration
            if (dispatcherConfig.shouldUseProps()) {
                // If dispatcher requires Props (blocking, fromConfig, sameAsParent), use Props
                // and apply mailbox configuration to Props as well
                Props props = dispatcherConfig.toProps();
                props = mailboxConfig.applyToProps(props);
                ref = ctx.spawn(behavior, actorName, props);
            } else {
                // If default dispatcher, use MailboxSelector
                ref = ctx.spawn(behavior, actorName, mailboxConfig.toMailboxSelector());
            }
        }

        @SuppressWarnings("unchecked")
        ActorRef<T> typedRef = (ActorRef<T>) ref;
        return typedRef;
    }
}

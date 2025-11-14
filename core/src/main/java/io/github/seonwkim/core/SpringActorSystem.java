package io.github.seonwkim.core;

import io.github.seonwkim.core.RootGuardian.Spawned;
import io.github.seonwkim.core.behavior.ClusterEventBehavior;
import io.github.seonwkim.core.impl.DefaultSpringActorContext;
import io.github.seonwkim.core.shard.ShardEnvelope;
import io.github.seonwkim.core.shard.ShardedActorRegistry;
import io.github.seonwkim.core.shard.SpringShardedActor;
import io.github.seonwkim.core.shard.SpringShardedActorBuilder;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import javax.annotation.Nullable;
import org.apache.pekko.actor.typed.*;
import org.apache.pekko.actor.typed.javadsl.AskPattern;
import org.apache.pekko.cluster.ClusterEvent;
import org.apache.pekko.cluster.sharding.typed.javadsl.ClusterSharding;
import org.apache.pekko.cluster.sharding.typed.javadsl.Entity;
import org.apache.pekko.cluster.typed.Cluster;
import org.apache.pekko.cluster.typed.ClusterSingleton;
import org.apache.pekko.cluster.typed.Subscribe;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.context.ApplicationEventPublisher;

/**
 * A wrapper around Pekko's ActorSystem that provides methods for spawning actors and getting
 * references to sharded actors. This class simplifies interaction with the actor system by
 * providing a more Spring-friendly API. It supports both local and cluster modes.
 *
 * <p>The SpringActorSystem is part of the actor system architecture:
 *
 * <ul>
 *   <li>SpringActor: Interface for actor implementations that can be managed by the Spring actor
 *       system
 *   <li>SpringActorSystem: Main entry point for creating and managing actors (this class)
 *   <li>RootGuardian: Internal component that manages the lifecycle of actors
 * </ul>
 *
 * <p>The SpringActorSystem delegates actor creation and management to the RootGuardian, which is
 * the top-level actor in the system. The RootGuardian maintains references to all actors and
 * handles commands for spawning and stopping actors.
 */
public class SpringActorSystem implements DisposableBean {

    private final ActorSystem<RootGuardian.Command> actorSystem;

    @Nullable private final Cluster cluster;

    @Nullable private final ClusterSharding clusterSharding;

    @Nullable private final ClusterSingleton clusterSingleton;

    private final Duration defaultQueryTimeout = Duration.ofMillis(100);

    private final Duration defaultActorRefTimeout = Duration.ofSeconds(3);

    /**
     * Creates a new SpringActorSystem in local mode.
     *
     * @param actorSystem The underlying Pekko ActorSystem
     */
    public SpringActorSystem(ActorSystem<RootGuardian.Command> actorSystem) {
        this.actorSystem = actorSystem;
        this.cluster = null;
        this.clusterSharding = null;
        this.clusterSingleton = null;
    }

    /**
     * Creates a new SpringActorSystem in cluster mode. This constructor also sets up a listener for
     * cluster events and publishes them as Spring application events.
     *
     * @param actorSystem The underlying Pekko ActorSystem
     * @param cluster The Pekko Cluster
     * @param clusterSharding The Pekko ClusterSharding
     * @param publisher The Spring ApplicationEventPublisher for publishing cluster events
     */
    public SpringActorSystem(
            ActorSystem<RootGuardian.Command> actorSystem,
            Cluster cluster,
            ClusterSharding clusterSharding,
            ApplicationEventPublisher publisher) {
        this(actorSystem, cluster, clusterSharding, ClusterSingleton.get(actorSystem), publisher);
    }

    /**
     * Creates a new SpringActorSystem in cluster mode. This constructor also sets up a listener for
     * cluster events and publishes them as Spring application events.
     * Uses the static ShardedActorRegistry for type resolution.
     *
     * @param actorSystem The underlying Pekko ActorSystem
     * @param cluster The Pekko Cluster
     * @param clusterSharding The Pekko ClusterSharding
     * @param clusterSingleton The Pekko ClusterSingleton
     * @param publisher The Spring ApplicationEventPublisher for publishing cluster events
     */
    public SpringActorSystem(
            ActorSystem<RootGuardian.Command> actorSystem,
            Cluster cluster,
            @Nullable ClusterSharding clusterSharding,
            @Nullable ClusterSingleton clusterSingleton,
            ApplicationEventPublisher publisher) {
        this.actorSystem = actorSystem;
        this.cluster = cluster;
        this.clusterSharding = clusterSharding;
        this.clusterSingleton = clusterSingleton;

        ActorRef<ClusterEvent.ClusterDomainEvent> listener = actorSystem.systemActorOf(
                ClusterEventBehavior.create(publisher), "cluster-event-listener", Props.empty());
        cluster.subscriptions().tell(new Subscribe<>(listener, ClusterEvent.ClusterDomainEvent.class));
    }

    /**
     * Returns the underlying Pekko ActorSystem.
     *
     * @return The raw Pekko ActorSystem
     */
    public ActorSystem<RootGuardian.Command> getRaw() {
        return actorSystem;
    }

    /**
     * Returns the Pekko Cluster if this SpringActorSystem is in cluster mode.
     *
     * @return The Pekko Cluster, or null if this SpringActorSystem is in local mode
     */
    @Nullable public Cluster getCluster() {
        return cluster;
    }

    @Nullable public ClusterSharding getClusterSharding() {
        return clusterSharding;
    }

    /**
     * Returns the Pekko ClusterSingleton if this SpringActorSystem is in cluster mode.
     *
     * @return The Pekko ClusterSingleton, or null if this SpringActorSystem is in local mode
     */
    @Nullable public ClusterSingleton getClusterSingleton() {
        return clusterSingleton;
    }

    /**
     * Creates a fluent builder for configuring and spawning an actor with the given actor class.
     *
     * <p>Example usage:
     *
     * <pre>
     * SpringActorRef&lt;Command&gt; actor = actorSystem
     *     .actor(HelloActor.class)
     *     .withId("myActor")
     *     .withTimeout(Duration.ofSeconds(5))
     *     .spawn();
     * </pre>
     *
     * @param actorClass The class of the actor
     * @param <A> The type of the actor
     * @param <C> The type of commands that the actor can handle
     * @return A builder for configuring and spawning the actor
     */
    public <A extends SpringActorWithContext<C, ?>, C> SpringActorSpawnBuilder<A, C> actor(Class<A> actorClass) {
        return new SpringActorSpawnBuilder<>(this, actorClass);
    }

    /**
     * Checks if an actor with the given class and ID exists in the actor system.
     *
     * @param actorClass The class of the actor
     * @param actorId The ID of the actor
     * @param <A> The type of the actor
     * @param <C> The type of commands that the actor can handle
     * @return A CompletionStage that completes with true if the actor exists, false otherwise
     */
    public <A extends SpringActorWithContext<C, ?>, C> CompletionStage<Boolean> exists(
            Class<A> actorClass, String actorId) {
        return exists(actorClass, actorId, defaultQueryTimeout);
    }

    /**
     * Checks if an actor with the given class and ID exists in the actor system with a custom timeout.
     *
     * @param actorClass The class of the actor
     * @param actorId The ID of the actor
     * @param timeout The maximum time to wait for the response
     * @param <A> The type of the actor
     * @param <C> The type of commands that the actor can handle
     * @return A CompletionStage that completes with true if the actor exists, false otherwise
     */
    public <A extends SpringActorWithContext<C, ?>, C> CompletionStage<Boolean> exists(
            Class<A> actorClass, String actorId, Duration timeout) {
        SpringActorContext actorContext = new DefaultSpringActorContext(actorId);

        return AskPattern.ask(
                        actorSystem,
                        (ActorRef<RootGuardian.ExistsResponse> replyTo) ->
                                new RootGuardian.CheckExists(actorClass, actorContext, replyTo),
                        timeout,
                        actorSystem.scheduler())
                .thenApply(response -> response.exists)
                .exceptionally(throwable -> false);
    }

    /**
     * Gets a reference to an existing actor with the given class and ID.
     *
     * @param actorClass The class of the actor
     * @param actorId The ID of the actor
     * @param <A> The type of the actor
     * @param <C> The type of commands that the actor can handle
     * @return A CompletionStage that completes with a SpringActorRef if found, or null if not found
     */
    public <A extends SpringActorWithContext<C, ?>, C> CompletionStage<SpringActorRef<C>> get(
            Class<A> actorClass, String actorId) {
        return get(actorClass, actorId, defaultQueryTimeout);
    }

    /**
     * Gets a reference to an existing actor with the given class and ID with a custom timeout.
     *
     * @param actorClass The class of the actor
     * @param actorId The ID of the actor
     * @param timeout The maximum time to wait for the response
     * @param <A> The type of the actor
     * @param <C> The type of commands that the actor can handle
     * @return A CompletionStage that completes with a SpringActorRef if found, or null if not found
     */
    public <A extends SpringActorWithContext<C, ?>, C> CompletionStage<SpringActorRef<C>> get(
            Class<A> actorClass, String actorId, Duration timeout) {
        SpringActorContext actorContext = new DefaultSpringActorContext(actorId);

        return AskPattern.ask(
                        actorSystem,
                        (ActorRef<RootGuardian.GetActorResponse<?>> replyTo) ->
                                new RootGuardian.GetActor(actorClass, actorContext, replyTo),
                        timeout,
                        actorSystem.scheduler())
                .thenApply(response -> {
                    if (response.ref == null) {
                        return null;
                    }

                    @SuppressWarnings("unchecked")
                    ActorRef<C> typedRef = (ActorRef<C>) response.ref;
                    return new SpringActorRef<>(actorSystem.scheduler(), typedRef, defaultActorRefTimeout);
                })
                .exceptionally(throwable -> null);
    }

    /**
     * Gets a reference to an existing actor, or spawns a new one if it doesn't exist.
     * This is a convenience method that combines exists/get/spawn logic.
     *
     * @param actorClass The class of the actor
     * @param actorId The ID of the actor
     * @param <A> The type of the actor
     * @param <C> The type of commands that the actor can handle
     * @return A CompletionStage that completes with a SpringActorRef (either existing or newly created)
     */
    public <A extends SpringActorWithContext<C, ?>, C> CompletionStage<SpringActorRef<C>> getOrSpawn(
            Class<A> actorClass, String actorId) {
        return getOrSpawn(actorClass, actorId, defaultActorRefTimeout);
    }

    /**
     * Gets a reference to an existing actor, or spawns a new one if it doesn't exist with a custom timeout.
     * This is a convenience method that combines exists/get/spawn logic.
     *
     * @param actorClass The class of the actor
     * @param actorId The ID of the actor
     * @param timeout The maximum time to wait for the response
     * @param <A> The type of the actor
     * @param <C> The type of commands that the actor can handle
     * @return A CompletionStage that completes with a SpringActorRef (either existing or newly created)
     */
    public <A extends SpringActorWithContext<C, ?>, C> CompletionStage<SpringActorRef<C>> getOrSpawn(
            Class<A> actorClass, String actorId, Duration timeout) {
        return exists(actorClass, actorId, timeout).thenCompose(exists -> {
            if (exists) {
                return get(actorClass, actorId, timeout);
            } else {
                return actor(actorClass).withId(actorId).withTimeout(timeout).spawn();
            }
        });
    }

    protected <A extends SpringActorWithContext<C, ?>, C> CompletionStage<SpringActorRef<C>> spawn(
            Class<A> actorClass,
            SpringActorContext actorContext,
            MailboxConfig mailboxConfig,
            DispatcherConfig dispatcherConfig,
            TagsConfig tagsConfig,
            boolean isClusterSingleton,
            @Nullable SupervisorStrategy supervisorStrategy,
            Duration timeout) {

        // If cluster singleton is requested, validate cluster mode
        if (isClusterSingleton && clusterSingleton == null) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException("Cluster singleton requested but cluster mode is not enabled. "
                            + "Ensure your application is running in cluster mode."));
        }

        return AskPattern.ask(
                        actorSystem,
                        (ActorRef<Spawned<?>> replyTo) -> new RootGuardian.SpawnActor(
                                actorClass,
                                actorContext,
                                replyTo,
                                mailboxConfig,
                                dispatcherConfig,
                                tagsConfig,
                                isClusterSingleton,
                                supervisorStrategy),
                        timeout,
                        actorSystem.scheduler())
                .thenApply(spawned -> {
                    @SuppressWarnings("unchecked")
                    ActorRef<C> typedRef = (ActorRef<C>) spawned.ref;
                    return new SpringActorRef<>(actorSystem.scheduler(), typedRef, defaultActorRefTimeout);
                });
    }

    /**
     * Creates a fluent builder for getting a reference to a sharded actor. This provides a simplified
     * API for working with sharded actors.
     *
     * <p>Example usage:
     *
     * <pre>
     * var counter = actorSystem.sharded(CounterActor.class)
     *     .withId("counter-123")
     *     .get();
     * </pre>
     *
     * @param actorClass The class of the sharded actor
     * @param <T> The type of commands that the sharded actor can handle
     * @return A builder for configuring and getting the sharded actor reference
     * @throws IllegalStateException If this SpringActorSystem is not in cluster mode
     */
    public <T> SpringShardedActorBuilder<T> sharded(Class<? extends SpringShardedActor<T>> actorClass) {
        if (clusterSharding == null) {
            throw new IllegalStateException("Cluster sharding not configured. Sharded actors require cluster mode.");
        }
        return new SpringShardedActorBuilder<>(this, actorClass);
    }

    /**
     * Returns the name of this actor system.
     *
     * @return The actor system name
     */
    public String getName() {
        return actorSystem.name();
    }

    /**
     * Checks if the actor system is running in cluster mode.
     *
     * @return true if cluster mode is enabled, false otherwise
     */
    public boolean isClusterMode() {
        return cluster != null;
    }

    /**
     * Initializes cluster sharding for all registered sharded actors.
     * This method should be called after all sharded actor beans are registered in the static registry.
     * It's typically called by a SmartInitializingSingleton to ensure proper initialization order.
     */
    public void initializeClusterSharding() {
        if (clusterSharding == null) {
            throw new IllegalStateException("Cluster sharding not available");
        }

        // Initialize all sharded actors from static registry
        for (SpringShardedActor<?> actor : ShardedActorRegistry.getAll()) {
            initShardedActor(actor);
        }
    }

    /**
     * Initializes a single sharded actor with cluster sharding.
     */
    private <T> void initShardedActor(SpringShardedActor<T> actor) {
        if (clusterSharding == null) {
            return;
        }

        Entity<T, ShardEnvelope<T>> entity = Entity.of(actor.typeKey(), entityCtx -> {
                    var shardedActorContext = actor.createContext(entityCtx);
                    return actor.create(shardedActorContext).asBehavior();
                })
                .withMessageExtractor(actor.extractor());
        clusterSharding.init(entity);
    }

    /**
     * Terminates the actor system and waits for it to terminate. This method is called by Spring when
     * the application context is closed.
     */
    @Override
    public void destroy() {
        actorSystem.terminate();
        actorSystem.getWhenTerminated().toCompletableFuture().join();
    }
}

package io.github.seonwkim.core;

import io.github.seonwkim.core.RootGuardian.Spawned;
import io.github.seonwkim.core.RootGuardian.StopResult;
import io.github.seonwkim.core.behavior.ClusterEventBehavior;
import io.github.seonwkim.core.impl.DefaultRootGuardian;

import java.time.Duration;
import java.util.concurrent.CompletionStage;

import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.actor.typed.Props;
import org.apache.pekko.actor.typed.javadsl.AskPattern;
import org.apache.pekko.cluster.ClusterEvent;
import org.apache.pekko.cluster.sharding.typed.javadsl.ClusterSharding;
import org.apache.pekko.cluster.sharding.typed.javadsl.EntityRef;
import org.apache.pekko.cluster.sharding.typed.javadsl.EntityTypeKey;
import org.apache.pekko.cluster.typed.Cluster;
import org.apache.pekko.cluster.typed.Subscribe;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.lang.Nullable;

/**
 * A wrapper around Pekko's ActorSystem that provides methods for spawning actors and getting entity
 * references. This class simplifies interaction with the actor system by providing a more
 * Spring-friendly API. It supports both local and cluster modes.
 *
 * <p>The SpringActorSystem is part of the actor system architecture:
 * <ul>
 *   <li>SpringActor: Interface for actor implementations that can be managed by the Spring actor system</li>
 *   <li>SpringActorSystem: Main entry point for creating and managing actors (this class)</li>
 *   <li>RootGuardian: Internal component that manages the lifecycle of actors</li>
 * </ul>
 *
 * <p>The SpringActorSystem delegates actor creation and management to the RootGuardian, which is the
 * top-level actor in the system. The RootGuardian maintains references to all actors and handles
 * commands for spawning and stopping actors.
 */
public class SpringActorSystem implements DisposableBean {

    private final ActorSystem<RootGuardian.Command> actorSystem;
    @Nullable
    private final Cluster cluster;
    @Nullable
    private final ClusterSharding clusterSharding;

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(3); // configurable if needed

    /**
     * Creates a new SpringActorSystem in local mode.
     *
     * @param actorSystem The underlying Pekko ActorSystem
     */
    public SpringActorSystem(ActorSystem<RootGuardian.Command> actorSystem) {
        this.actorSystem = actorSystem;
        this.cluster = null;
        this.clusterSharding = null;
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
        this.actorSystem = actorSystem;
        this.cluster = cluster;
        this.clusterSharding = clusterSharding;

        ActorRef<ClusterEvent.ClusterDomainEvent> listener =
                actorSystem.systemActorOf(
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
    @Nullable
    public Cluster getCluster() {
        return cluster;
    }

    /**
     * Spawns a new actor with the given spawn context.
     *
     * @param spawnContext The context containing all parameters needed to spawn the actor
     * @param <A> The type of the actor
     * @param <C> The type of commands that the actor can handle
     *
     * @return A CompletionStage that will be completed with a reference to the spawned actor
     */
    @SuppressWarnings("unchecked")
    public <A extends SpringActor<A, C>, C> CompletionStage<SpringActorRef<C>> spawn(
            SpringActorSpawnContext<A, C> spawnContext
    ) {
        return AskPattern.ask(actorSystem,
                              (ActorRef<Spawned<?>> replyTo) ->
                                      new DefaultRootGuardian.SpawnActor(
                                              spawnContext.getActorClass(),
                                              spawnContext.getActorContext(),
                                              replyTo,
                                              spawnContext.getMailboxSelector(),
                                              spawnContext.isClusterSingleton()),
                              spawnContext.getTimeout(),
                              actorSystem.scheduler())
                         .thenApply(spawned -> {
                             // This cast is necessary because the Spawned message contains a generic ActorRef<?>
                             // We know it's an ActorRef<C> because we spawned an actor that handles commands of type C
                             return new SpringActorRef<>(actorSystem.scheduler(), (ActorRef<C>) spawned.ref);
                         });
    }

    /**
     * Spawns a new actor with the given actor class and actor ID.
     * This is a simplified version of the {@link #spawn(SpringActorSpawnContext)} method.
     *
     * @param actorClass The class of the actor to spawn
     * @param actorId The ID of the actor to spawn
     * @param <A> The type of the actor
     * @param <C> The type of commands that the actor can handle
     *
     * @return A CompletionStage that will be completed with a reference to the spawned actor
     */
    public <A extends SpringActor<A, C>, C> CompletionStage<SpringActorRef<C>> spawn(Class<A> actorClass,
                                                                                     String actorId) {
        SpringActorSpawnContext<A, C> spawnContext = new SpringActorSpawnContext.Builder<A, C>()
                .actorClass(actorClass)
                .actorId(actorId)
                .build();
        return spawn(spawnContext);
    }

    /**
     * Spawns a new actor with the given actor class and actor context.
     * This is a simplified version of the {@link #spawn(SpringActorSpawnContext)} method.
     *
     * @param actorClass The class of the actor to spawn
     * @param actorContext The context of the actor to spawn
     * @param <A> The type of the actor
     * @param <C> The type of commands that the actor can handle
     *
     * @return A CompletionStage that will be completed with a reference to the spawned actor
     */
    public <A extends SpringActor<A, C>, C> CompletionStage<SpringActorRef<C>> spawn(Class<A> actorClass,
                                                                                     SpringActorContext actorContext) {
        SpringActorSpawnContext<A, C> spawnContext = new SpringActorSpawnContext.Builder<A, C>()
                .actorClass(actorClass)
                .actorContext(actorContext)
                .build();
        return spawn(spawnContext);
    }

    /**
     * Asynchronously stops a previously spawned actor with the given stop context.
     *
     * <p>If the actor exists and is currently active, it will be gracefully stopped. If the actor
     * does not exist or has already been passivated or stopped, the returned {@link CompletionStage}
     * will still complete successfully with a {@link StopResult} response indicating the request was
     * acknowledged.
     *
     * @param stopContext The context containing all parameters needed to stop the actor
     *
     * @return A {@link CompletionStage} that completes when the stop command has been processed
     */
    public <A extends SpringActor<A, C>, C> CompletionStage<StopResult> stop(
            SpringActorStopContext<A, C> stopContext) {
        return AskPattern.ask(
                actorSystem,
                (ActorRef<DefaultRootGuardian.StopResult> replyTo) ->
                        new DefaultRootGuardian.StopActor(
                                stopContext.getActorClass(),
                                stopContext.getActorContext(),
                                replyTo),
                stopContext.getTimeout(),
                actorSystem.scheduler());
    }

    /**
     * Asynchronously stops a previously spawned actor with the given actor class and actor ID.
     * This is a simplified version of the {@link #stop(SpringActorStopContext)} method.
     *
     * <p>If the actor exists and is currently active, it will be gracefully stopped. If the actor
     * does not exist or has already been passivated or stopped, the returned {@link CompletionStage}
     * will still complete successfully with a {@link StopResult} response indicating the request was
     * acknowledged.
     *
     * @param actorClass The class of the actor to stop
     * @param actorId The ID of the actor to stop
     * @param <A> The type of the actor
     * @param <C> The type of commands that the actor can handle
     *
     * @return A {@link CompletionStage} that completes when the stop command has been processed
     */
    public <A extends SpringActor<A, C>, C> CompletionStage<StopResult> stop(Class<A> actorClass,
                                                                             String actorId) {
        SpringActorStopContext<A, C> stopContext = new SpringActorStopContext.Builder<A, C>()
                .actorClass(actorClass)
                .actorId(actorId)
                .build();
        return stop(stopContext);
    }

    /**
     * Asynchronously stops a previously spawned actor with the given actor class and actor context.
     * This is a simplified version of the {@link #stop(SpringActorStopContext)} method.
     *
     * <p>If the actor exists and is currently active, it will be gracefully stopped. If the actor
     * does not exist or has already been passivated or stopped, the returned {@link CompletionStage}
     * will still complete successfully with a {@link StopResult} response indicating the request was
     * acknowledged.
     *
     * @param actorClass The class of the actor to stop
     * @param actorContext The context of the actor to stop
     * @param <A> The type of the actor
     * @param <C> The type of commands that the actor can handle
     *
     * @return A {@link CompletionStage} that completes when the stop command has been processed
     */
    public <A extends SpringActor<A, C>, C> CompletionStage<StopResult> stop(Class<A> actorClass,
                                                                             SpringActorContext actorContext) {
        SpringActorStopContext<A, C> stopContext = new SpringActorStopContext.Builder<A, C>()
                .actorClass(actorClass)
                .actorContext(actorContext)
                .build();
        return stop(stopContext);
    }

    /**
     * Returns a reference to a sharded entity with the given entity type key and entity ID. This
     * method can only be called if this SpringActorSystem is in cluster mode.
     *
     * @param entityTypeKey The entity type key
     * @param entityId The entity ID
     * @param <T> The type of messages that the entity can handle
     *
     * @return A SpringShardedActorRef to the entity
     *
     * @throws IllegalStateException If this SpringActorSystem is not in cluster mode
     */
    public <T> SpringShardedActorRef<T> entityRef(EntityTypeKey<T> entityTypeKey, String entityId) {
        if (clusterSharding == null) {
            throw new IllegalStateException("Cluster sharding not configured");
        }

        final EntityRef<T> entityRef = clusterSharding.entityRefFor(entityTypeKey, entityId);
        return new SpringShardedActorRef<>(actorSystem.scheduler(), entityRef);
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

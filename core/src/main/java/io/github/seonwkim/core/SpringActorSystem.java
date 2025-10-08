package io.github.seonwkim.core;

import io.github.seonwkim.core.RootGuardian.Spawned;
import io.github.seonwkim.core.behavior.ClusterEventBehavior;
import io.github.seonwkim.core.impl.DefaultRootGuardian;
import io.github.seonwkim.core.shard.ShardedActor;
import java.time.Duration;
import java.util.concurrent.CompletionStage;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.actor.typed.MailboxSelector;
import org.apache.pekko.actor.typed.Props;
import org.apache.pekko.actor.typed.javadsl.AskPattern;
import org.apache.pekko.cluster.ClusterEvent;
import org.apache.pekko.cluster.sharding.typed.javadsl.ClusterSharding;
import org.apache.pekko.cluster.typed.Cluster;
import org.apache.pekko.cluster.typed.Subscribe;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.lang.Nullable;

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
     * Creates a fluent builder for spawning an actor with the given actor class. This provides a more
     * convenient API for configuring and spawning actors.
     *
     * <p>Example usage:
     *
     * <pre>
     * SpringActorRef&lt;Command&gt; actor = actorSystem
     *     .spawn(HelloActor.class)
     *     .withId("myActor")
     *     .withTimeout(Duration.ofSeconds(5))
     *     .start();
     * </pre>
     *
     * @param actorClass The class of the actor to spawn
     * @param <A> The type of the actor
     * @param <C> The type of commands that the actor can handle
     * @return A builder for configuring and spawning the actor
     */
    public <A extends SpringActor<A, C>, C> SpringActorSpawnBuilder<A, C> spawn(Class<A> actorClass) {
        return new SpringActorSpawnBuilder<>(this, actorClass);
    }

    protected <A extends SpringActor<A, C>, C> CompletionStage<SpringActorRef<C>> spawn(
            Class<A> actorClass,
            SpringActorContext actorContext,
            MailboxSelector mailboxSelector,
            boolean isClusterSingleton,
            Duration timeout) {
        return AskPattern.ask(
                        actorSystem,
                        (ActorRef<Spawned<?>> replyTo) -> new DefaultRootGuardian.SpawnActor(
                                actorClass, actorContext, replyTo, mailboxSelector, isClusterSingleton),
                        timeout,
                        actorSystem.scheduler())
                .thenApply(spawned -> {
                    // Safe cast: spawn context ensures actor class matches command type C
                    @SuppressWarnings("unchecked")
                    ActorRef<C> typedRef = (ActorRef<C>) spawned.ref;
                    return new SpringActorRef<>(
                            actorSystem.scheduler(),
                            typedRef,
                            Duration.ofSeconds(3),
                            actorSystem,
                            actorClass,
                            actorContext);
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
    public <T> SpringShardedActorBuilder<T> sharded(Class<? extends ShardedActor<T>> actorClass) {
        if (clusterSharding == null) {
            throw new IllegalStateException("Cluster sharding not configured. Sharded actors require cluster mode.");
        }
        return new SpringShardedActorBuilder<>(this, actorClass);
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

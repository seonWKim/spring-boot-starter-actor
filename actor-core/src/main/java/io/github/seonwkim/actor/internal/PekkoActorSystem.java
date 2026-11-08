package io.github.seonwkim.actor.internal;

import io.github.seonwkim.actor.ActorConstants;
import io.github.seonwkim.actor.ActorSystemShutdownLifecycle;
import io.github.seonwkim.actor.DispatcherConfig;
import io.github.seonwkim.actor.MailboxConfig;
import io.github.seonwkim.actor.SpringActorContext;
import io.github.seonwkim.actor.SpringActorHandle;
import io.github.seonwkim.actor.SpringActorSpawnBuilder;
import io.github.seonwkim.actor.SpringActorSystem;
import io.github.seonwkim.actor.SpringActorWithContext;
import io.github.seonwkim.actor.SupervisionStrategy;
import io.github.seonwkim.actor.TagsConfig;
import io.github.seonwkim.actor.behavior.ClusterEventBehavior;
import io.github.seonwkim.actor.internal.RootGuardian.Spawned;
import io.github.seonwkim.actor.shard.ShardEnvelope;
import io.github.seonwkim.actor.shard.ShardedActorRegistry;
import io.github.seonwkim.actor.shard.SpringShardedActor;
import io.github.seonwkim.actor.shard.SpringShardedActorBuilder;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.annotation.Nullable;
import org.apache.pekko.actor.CoordinatedShutdown;
import org.apache.pekko.actor.typed.*;
import org.apache.pekko.actor.typed.javadsl.AskPattern;
import org.apache.pekko.cluster.ClusterEvent;
import org.apache.pekko.cluster.sharding.typed.javadsl.ClusterSharding;
import org.apache.pekko.cluster.sharding.typed.javadsl.Entity;
import org.apache.pekko.cluster.typed.Cluster;
import org.apache.pekko.cluster.typed.ClusterSingleton;
import org.apache.pekko.cluster.typed.Subscribe;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.context.ApplicationEventPublisher;

/** Pekko-backed {@link SpringActorSystem}. */
public final class PekkoActorSystem implements SpringActorSystem, DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(PekkoActorSystem.class);

    /** The coordinated shutdown reason used when the Spring application context closes. */
    public static final CoordinatedShutdown.Reason SPRING_CONTEXT_CLOSED = new SpringContextClosedReason();

    private static final class SpringContextClosedReason implements CoordinatedShutdown.Reason {
        @Override
        public String toString() {
            return "SpringContextClosed";
        }
    }

    private final AtomicBoolean shutdownStarted = new AtomicBoolean(false);

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
    public PekkoActorSystem(ActorSystem<RootGuardian.Command> actorSystem) {
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
    public PekkoActorSystem(
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
    public PekkoActorSystem(
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
    @Override
    public <X> X unwrap(Class<X> type) {
        for (Object candidate : new Object[] {actorSystem, cluster, clusterSharding, clusterSingleton}) {
            if (type.isInstance(candidate)) {
                return type.cast(candidate);
            }
        }
        throw new IllegalArgumentException("Cannot unwrap the actor system to " + type.getName());
    }

    /** The Pekko actor system behind any {@link SpringActorSystem}. */
    @SuppressWarnings("unchecked")
    public static ActorSystem<RootGuardian.Command> raw(SpringActorSystem system) {
        return system.unwrap(ActorSystem.class);
    }

    /** The Pekko actor system. */
    public ActorSystem<RootGuardian.Command> getRaw() {
        return actorSystem;
    }

    @Override
    @Nullable public Cluster getCluster() {
        return cluster;
    }

    @Override
    @Nullable public ClusterSharding getClusterSharding() {
        return clusterSharding;
    }

    @Override
    @Nullable public ClusterSingleton getClusterSingleton() {
        return clusterSingleton;
    }

    @Override
    public <A extends SpringActorWithContext<C, ?>, C> SpringActorSpawnBuilder<A, C> actor(Class<A> actorClass) {
        return new PekkoActorSpawnBuilder<>(this, actorClass);
    }

    @Override
    public <A extends SpringActorWithContext<C, ?>, C> CompletionStage<Boolean> exists(
            Class<A> actorClass, String actorId) {
        return exists(actorClass, actorId, defaultQueryTimeout);
    }

    @Override
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

    @Override
    public <A extends SpringActorWithContext<C, ?>, C> CompletionStage<SpringActorHandle<C>> get(
            Class<A> actorClass, String actorId) {
        return get(actorClass, actorId, defaultQueryTimeout);
    }

    @Override
    public <A extends SpringActorWithContext<C, ?>, C> CompletionStage<SpringActorHandle<C>> get(
            Class<A> actorClass, String actorId, Duration timeout) {
        SpringActorContext actorContext = new DefaultSpringActorContext(actorId);

        return AskPattern.ask(
                        actorSystem,
                        (ActorRef<RootGuardian.GetActorResponse<?>> replyTo) ->
                                new RootGuardian.GetActor(actorClass, actorContext, replyTo),
                        timeout,
                        actorSystem.scheduler())
                .<SpringActorHandle<C>>thenApply(response -> {
                    if (response.ref == null) {
                        return null;
                    }

                    @SuppressWarnings("unchecked")
                    ActorRef<C> typedRef = (ActorRef<C>) response.ref;
                    return new PekkoActorHandle<>(actorSystem.scheduler(), typedRef, defaultActorRefTimeout);
                })
                .exceptionally(throwable -> null);
    }

    @Override
    public <A extends SpringActorWithContext<C, ?>, C> CompletionStage<SpringActorHandle<C>> getOrSpawn(
            Class<A> actorClass, String actorId) {
        return getOrSpawn(actorClass, actorId, defaultActorRefTimeout);
    }

    @Override
    public <A extends SpringActorWithContext<C, ?>, C> CompletionStage<SpringActorHandle<C>> getOrSpawn(
            Class<A> actorClass, String actorId, Duration timeout) {
        return exists(actorClass, actorId, timeout).thenCompose(exists -> {
            if (exists) {
                return get(actorClass, actorId, timeout);
            } else {
                return actor(actorClass).withId(actorId).withTimeout(timeout).spawn();
            }
        });
    }

    public <A extends SpringActorWithContext<C, ?>, C> CompletionStage<SpringActorHandle<C>> spawn(
            Class<A> actorClass,
            SpringActorContext actorContext,
            MailboxConfig mailboxConfig,
            DispatcherConfig dispatcherConfig,
            TagsConfig tagsConfig,
            boolean isClusterSingleton,
            @Nullable SupervisionStrategy supervisorStrategy,
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
                    return new PekkoActorHandle<>(actorSystem.scheduler(), typedRef, defaultActorRefTimeout);
                });
    }

    @Override
    public <T> SpringShardedActorBuilder<T> sharded(Class<? extends SpringShardedActor<T>> actorClass) {
        if (clusterSharding == null) {
            throw new IllegalStateException("Cluster sharding not configured. Sharded actors require cluster mode.");
        }
        return new SpringShardedActorBuilder<>(this, actorClass);
    }

    @Override
    public String getName() {
        return actorSystem.name();
    }

    @Override
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
     * If the actor specifies a role, the entity will only be created on nodes with that role.
     * On nodes without the role, a proxy will be created to forward messages.
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

        // Apply role if specified - Entity.withRole() returns a new Entity object
        if (actor.role().isPresent()) {
            entity = entity.withRole(actor.role().get());
        }

        clusterSharding.init(entity);
    }

    @Override
    public ShutdownOutcome shutdown(Duration timeout) {
        CompletableFuture<?> terminated = actorSystem.getWhenTerminated().toCompletableFuture();
        if (terminated.isDone()) {
            shutdownStarted.set(true);
            return ShutdownOutcome.ALREADY_TERMINATED;
        }
        if (!shutdownStarted.compareAndSet(false, true)) {
            return ShutdownOutcome.ALREADY_IN_PROGRESS;
        }

        log.info("Running coordinated shutdown of actor system [{}] (timeout {})", actorSystem.name(), timeout);
        CoordinatedShutdown.get(actorSystem)
                .runAll(SPRING_CONTEXT_CLOSED)
                // In case coordinated shutdown is configured not to terminate the actor system.
                .whenComplete((done, error) -> actorSystem.terminate());

        try {
            terminated.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            return ShutdownOutcome.TERMINATED;
        } catch (TimeoutException e) {
            log.warn(
                    "Actor system [{}] did not terminate within {}; continuing Spring context close",
                    actorSystem.name(),
                    timeout);
            return ShutdownOutcome.TIMED_OUT;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted while waiting for actor system [{}] to terminate", actorSystem.name());
            return ShutdownOutcome.TIMED_OUT;
        } catch (ExecutionException e) {
            log.warn("Actor system [{}] terminated with an error", actorSystem.name(), e.getCause());
            return ShutdownOutcome.TERMINATED;
        }
    }

    /**
     * Called by Spring when the application context is closed. Normally {@link
     * ActorSystemShutdownLifecycle} has already shut the actor system down by then, so this does
     * nothing; otherwise it runs {@link #shutdown(Duration)} with the default timeout.
     */
    @Override
    public void destroy() {
        shutdown(ActorConstants.DEFAULT_SHUTDOWN_TIMEOUT);
    }
}

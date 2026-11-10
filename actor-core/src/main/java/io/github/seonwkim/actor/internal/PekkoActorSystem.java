package io.github.seonwkim.actor.internal;

import io.github.seonwkim.actor.ActorConstants;
import io.github.seonwkim.actor.DispatcherConfig;
import io.github.seonwkim.actor.MailboxConfig;
import io.github.seonwkim.actor.SpringActorContext;
import io.github.seonwkim.actor.SpringActorHandle;
import io.github.seonwkim.actor.SpringActorSpawnBuilder;
import io.github.seonwkim.actor.SpringActorSystem;
import io.github.seonwkim.actor.SpringActorWithContext;
import io.github.seonwkim.actor.SupervisionStrategy;
import io.github.seonwkim.actor.TagsConfig;
import io.github.seonwkim.actor.internal.RootGuardian.Spawned;
import io.github.seonwkim.actor.shard.SpringShardedActor;
import io.github.seonwkim.actor.shard.SpringShardedActorBuilder;
import java.time.Duration;
import java.util.Optional;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;

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

    @Nullable private final ClusterSupport.ClusterRuntime cluster;

    private final Duration defaultActorRefTimeout = Duration.ofSeconds(3);

    /** A system in local mode. */
    public PekkoActorSystem(ActorSystem<RootGuardian.Command> actorSystem) {
        this.actorSystem = actorSystem;
        this.cluster = null;
    }

    /** A system in cluster mode, with the cluster features {@code cluster} started on it. */
    public PekkoActorSystem(ActorSystem<RootGuardian.Command> actorSystem, ClusterSupport.ClusterRuntime cluster) {
        this.actorSystem = actorSystem;
        this.cluster = cluster;
    }

    /**
     * Returns the underlying Pekko ActorSystem.
     *
     * @return The raw Pekko ActorSystem
     */
    @Override
    public <X> X unwrap(Class<X> type) {
        if (type.isInstance(actorSystem)) {
            return type.cast(actorSystem);
        }
        if (cluster != null) {
            for (Object candidate : cluster.engineObjects()) {
                if (type.isInstance(candidate)) {
                    return type.cast(candidate);
                }
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
    public <A extends SpringActorWithContext<C, ?>, C> SpringActorSpawnBuilder<A, C> actor(Class<A> actorClass) {
        return new PekkoActorSpawnBuilder<>(this, actorClass);
    }

    @Override
    public <A extends SpringActorWithContext<C, ?>, C> CompletionStage<Boolean> exists(
            Class<A> actorClass, String actorId) {
        return exists(actorClass, actorId, ActorConstants.DEFAULT_TIMEOUT);
    }

    @Override
    public <A extends SpringActorWithContext<C, ?>, C> CompletionStage<Boolean> exists(
            Class<A> actorClass, String actorId, Duration timeout) {
        SpringActorContext actorContext = new DefaultSpringActorContext(actorId);

        // A timeout fails the stage: a busy system must not report a running actor as missing.
        return AskPattern.ask(
                        actorSystem,
                        (ActorRef<RootGuardian.ExistsResponse> replyTo) ->
                                new RootGuardian.CheckExists(actorClass, actorContext, replyTo),
                        timeout,
                        actorSystem.scheduler())
                .thenApply(response -> response.exists);
    }

    @Override
    public <A extends SpringActorWithContext<C, ?>, C> CompletionStage<Optional<SpringActorHandle<C>>> find(
            Class<A> actorClass, String actorId) {
        return find(actorClass, actorId, ActorConstants.DEFAULT_TIMEOUT);
    }

    @Override
    public <A extends SpringActorWithContext<C, ?>, C> CompletionStage<Optional<SpringActorHandle<C>>> find(
            Class<A> actorClass, String actorId, Duration timeout) {
        SpringActorContext actorContext = new DefaultSpringActorContext(actorId);

        // A timeout fails the stage: a busy system must not report a running actor as missing.
        return AskPattern.ask(
                        actorSystem,
                        (ActorRef<RootGuardian.GetActorResponse<?>> replyTo) ->
                                new RootGuardian.GetActor(actorClass, actorContext, replyTo),
                        timeout,
                        actorSystem.scheduler())
                .thenApply(response -> {
                    if (response.ref == null) {
                        return Optional.empty();
                    }

                    @SuppressWarnings("unchecked")
                    ActorRef<C> typedRef = (ActorRef<C>) response.ref;
                    return Optional.of(new PekkoActorHandle<>(actorSystem, typedRef, defaultActorRefTimeout));
                });
    }

    @Override
    @Deprecated
    public <A extends SpringActorWithContext<C, ?>, C> CompletionStage<SpringActorHandle<C>> get(
            Class<A> actorClass, String actorId) {
        return get(actorClass, actorId, ActorConstants.DEFAULT_TIMEOUT);
    }

    @Override
    @Deprecated
    public <A extends SpringActorWithContext<C, ?>, C> CompletionStage<SpringActorHandle<C>> get(
            Class<A> actorClass, String actorId, Duration timeout) {
        return find(actorClass, actorId, timeout).thenApply(found -> found.orElse(null));
    }

    @Override
    public <A extends SpringActorWithContext<C, ?>, C> CompletionStage<SpringActorHandle<C>> getOrSpawn(
            Class<A> actorClass, String actorId) {
        return getOrSpawn(actorClass, actorId, defaultActorRefTimeout);
    }

    @Override
    public <A extends SpringActorWithContext<C, ?>, C> CompletionStage<SpringActorHandle<C>> getOrSpawn(
            Class<A> actorClass, String actorId, Duration timeout) {
        return actor(actorClass).withId(actorId).withTimeout(timeout).getOrSpawn();
    }

    public <A extends SpringActorWithContext<C, ?>, C> CompletionStage<SpringActorHandle<C>> spawn(
            Class<A> actorClass,
            SpringActorContext actorContext,
            MailboxConfig mailboxConfig,
            DispatcherConfig dispatcherConfig,
            TagsConfig tagsConfig,
            boolean isClusterSingleton,
            @Nullable SupervisionStrategy supervisorStrategy,
            boolean returnExisting,
            Duration timeout) {

        // If cluster singleton is requested, validate cluster mode
        if (isClusterSingleton && cluster == null) {
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
                                supervisorStrategy,
                                returnExisting),
                        timeout,
                        actorSystem.scheduler())
                .thenCompose(spawned -> {
                    if (spawned.error != null || spawned.ref == null) {
                        return CompletableFuture.failedFuture(new IllegalStateException(
                                "Could not spawn " + actorClass.getName() + " with id '" + actorContext.actorId()
                                        + "': " + (spawned.error == null ? "no actor" : spawned.error.getMessage()),
                                spawned.error));
                    }
                    @SuppressWarnings("unchecked")
                    ActorRef<C> typedRef = (ActorRef<C>) spawned.ref;
                    return CompletableFuture.completedFuture(
                            new PekkoActorHandle<>(actorSystem, typedRef, defaultActorRefTimeout));
                });
    }

    @Override
    public <T> SpringShardedActorBuilder<T> sharded(Class<? extends SpringShardedActor<T>> actorClass) {
        if (cluster == null) {
            throw new IllegalStateException("Cluster sharding not configured. Sharded actors require cluster mode.");
        }
        return cluster.sharded(actorClass);
    }

    @Override
    public String getName() {
        return actorSystem.name();
    }

    @Override
    public String nodeAddress() {
        return actorSystem.address().toString();
    }

    @Override
    public boolean isClusterMode() {
        return cluster != null;
    }

    /** Starts publishing cluster events as Spring events; a no-op in local mode. */
    public void publishClusterEvents() {
        if (cluster != null) {
            cluster.publishEvents();
        }
    }

    /** Registers the sharded actor beans with sharding; a no-op in local mode. */
    public void initializeClusterSharding() {
        if (cluster != null) {
            cluster.initSharding();
        }
    }

    @Override
    public CompletionStage<Void> whenTerminated() {
        return actorSystem.getWhenTerminated().thenApply(done -> null);
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
     * Called by Spring when the application context is closed. Normally {@code
     * ActorSystemShutdownLifecycle} has already shut the actor system down by then, so this does
     * nothing; otherwise it runs {@link #shutdown(Duration)} with the default timeout.
     */
    @Override
    public void destroy() {
        shutdown(ActorConstants.DEFAULT_SHUTDOWN_TIMEOUT);
    }
}

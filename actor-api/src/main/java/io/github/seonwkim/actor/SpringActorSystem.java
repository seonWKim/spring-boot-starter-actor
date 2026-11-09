package io.github.seonwkim.actor;

import io.github.seonwkim.actor.shard.SpringShardedActor;
import io.github.seonwkim.actor.shard.SpringShardedActorBuilder;
import java.time.Duration;
import java.util.concurrent.CompletionStage;

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
public interface SpringActorSystem {

    /** What a call to {@link #shutdown(Duration)} did. */
    enum ShutdownOutcome {
        /** This call ran coordinated shutdown and the actor system terminated within the timeout. */
        TERMINATED,
        /** The actor system had already terminated (for example after being downed); nothing was done. */
        ALREADY_TERMINATED,
        /** Shutdown was already started by an earlier call; this call did not wait again. */
        ALREADY_IN_PROGRESS,
        /** This call ran coordinated shutdown but the actor system did not terminate within the timeout. */
        TIMED_OUT
    }

    /**
     * Escape hatch: an object of the underlying engine, e.g. Pekko's
     * {@code org.apache.pekko.actor.typed.ActorSystem}. Code that uses it depends on the engine and
     * gets no compatibility promise from the starter.
     *
     * @throws IllegalArgumentException when the actor system has no object of that type
     */
    <X> X unwrap(Class<X> type);

    /**
     * Creates a fluent builder for configuring and spawning an actor with the given actor class.
     *
     * <p>Example usage:
     *
     * <pre>
     * SpringActorHandle&lt;Command&gt; actor = actorSystem
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
    <A extends SpringActorWithContext<C, ?>, C> SpringActorSpawnBuilder<A, C> actor(Class<A> actorClass);

    /**
     * Checks if an actor with the given class and ID exists in the actor system.
     *
     * @param actorClass The class of the actor
     * @param actorId The ID of the actor
     * @param <A> The type of the actor
     * @param <C> The type of commands that the actor can handle
     * @return A CompletionStage that completes with true if the actor exists, false otherwise
     */
    <A extends SpringActorWithContext<C, ?>, C> CompletionStage<Boolean> exists(Class<A> actorClass, String actorId);

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
    <A extends SpringActorWithContext<C, ?>, C> CompletionStage<Boolean> exists(
            Class<A> actorClass, String actorId, Duration timeout);

    /**
     * Gets a reference to an existing actor with the given class and ID.
     *
     * @param actorClass The class of the actor
     * @param actorId The ID of the actor
     * @param <A> The type of the actor
     * @param <C> The type of commands that the actor can handle
     * @return A CompletionStage that completes with a SpringActorHandle if found, or null if not found
     */
    <A extends SpringActorWithContext<C, ?>, C> CompletionStage<SpringActorHandle<C>> get(
            Class<A> actorClass, String actorId);

    /**
     * Gets a reference to an existing actor with the given class and ID with a custom timeout.
     *
     * @param actorClass The class of the actor
     * @param actorId The ID of the actor
     * @param timeout The maximum time to wait for the response
     * @param <A> The type of the actor
     * @param <C> The type of commands that the actor can handle
     * @return A CompletionStage that completes with a SpringActorHandle if found, or null if not found
     */
    <A extends SpringActorWithContext<C, ?>, C> CompletionStage<SpringActorHandle<C>> get(
            Class<A> actorClass, String actorId, Duration timeout);

    /**
     * Gets a reference to an existing actor, or spawns a new one if it doesn't exist.
     * This is a convenience method that combines exists/get/spawn logic.
     *
     * @param actorClass The class of the actor
     * @param actorId The ID of the actor
     * @param <A> The type of the actor
     * @param <C> The type of commands that the actor can handle
     * @return A CompletionStage that completes with a SpringActorHandle (either existing or newly created)
     */
    <A extends SpringActorWithContext<C, ?>, C> CompletionStage<SpringActorHandle<C>> getOrSpawn(
            Class<A> actorClass, String actorId);

    /**
     * Gets a reference to an existing actor, or spawns a new one if it doesn't exist with a custom timeout.
     * This is a convenience method that combines exists/get/spawn logic.
     *
     * @param actorClass The class of the actor
     * @param actorId The ID of the actor
     * @param timeout The maximum time to wait for the response
     * @param <A> The type of the actor
     * @param <C> The type of commands that the actor can handle
     * @return A CompletionStage that completes with a SpringActorHandle (either existing or newly created)
     */
    <A extends SpringActorWithContext<C, ?>, C> CompletionStage<SpringActorHandle<C>> getOrSpawn(
            Class<A> actorClass, String actorId, Duration timeout);

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
    <T> SpringShardedActorBuilder<T> sharded(Class<? extends SpringShardedActor<T>> actorClass);

    /**
     * Returns the name of this actor system.
     *
     * @return The actor system name
     */
    String getName();

    /**
     * The address other nodes reach this node at: {@code pekko://<name>@<host>:<port>} with the remote
     * or cluster provider, {@code pekko://<name>} for a local system.
     *
     * @return This node's address
     */
    String nodeAddress();

    /**
     * Checks if the actor system is running in cluster mode.
     *
     * @return true if cluster mode is enabled, false otherwise
     */
    boolean isClusterMode();

    /**
     * Runs Pekko coordinated shutdown and waits at most {@code timeout} for the actor system to
     * terminate. Coordinated shutdown leaves the cluster gracefully, so other nodes see this node as
     * Removed instead of unreachable.
     *
     * <p>Safe to call more than once: only the first call runs coordinated shutdown and waits. If the
     * actor system has already terminated (for example after being downed), the call does nothing.
     *
     * <p>In a Spring application the context follows the actor system: a shutdown that the context
     * did not start closes the context and, unless {@code spring.actor.shutdown.exit-process=false},
     * exits the process with status 1. To stop the application, close the context instead.
     *
     * @param timeout The maximum time to wait for the actor system to terminate
     * @return What this call did
     */
    ShutdownOutcome shutdown(Duration timeout);

    /**
     * Completes when the actor system has terminated, whether through {@link #shutdown(Duration)},
     * the Spring context closing, or the actor system stopping by itself (for example after this
     * node was downed).
     *
     * @return A stage that completes once the actor system has terminated
     */
    CompletionStage<Void> whenTerminated();
}

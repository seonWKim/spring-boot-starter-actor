package io.github.seonwkim.core;

import java.util.List;
import java.util.Optional;
import org.apache.pekko.actor.ActorPath;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.Scheduler;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.slf4j.Logger;

/**
 * Spring-enhanced wrapper around Pekko's ActorContext used during behavior creation.
 *
 * <p>This class provides a bridge between Pekko's actor system and Spring Boot Starter Actor,
 * offering convenient methods for common actor operations.
 *
 * <p>Key features:
 * <ul>
 *   <li>Full access to underlying Pekko ActorContext via {@link #getUnderlying()}
 *   <li>Convenient delegate methods for common ActorContext operations
 *   <li>Child actor spawning and management
 * </ul>
 *
 * <p>Example usage in behavior creation:
 * <pre>
 * {@code
 * @Override
 * public SpringActorBehavior<Command> create(MyContext myContext) {
 *     return SpringActorBehavior.builder(Command.class, myContext)
 *         .withState(ctx -> {
 *             // Access context methods
 *             ctx.getLog().info("Actor starting");
 *             return new MyBehavior(ctx);
 *         })
 *         .onMessage(DoWork.class, MyBehavior::handleWork)
 *         .build();
 * }
 * }
 * </pre>
 *
 * @param <T> The message type that the actor handles
 * @see SpringActorBehavior
 */
public final class SpringBehaviorContext<T> {

    private final ActorContext<T> underlying;

    /**
     * Creates a new SpringBehaviorContext wrapping the given ActorContext.
     *
     * @param underlying The Pekko ActorContext to wrap
     */
    public SpringBehaviorContext(ActorContext<T> underlying) {
        this.underlying = underlying;
    }

    /**
     * Returns the underlying Pekko ActorContext.
     *
     * <p>Use this when you need direct access to Pekko-specific functionality not exposed
     * through this wrapper's convenience methods.
     *
     * @return The underlying ActorContext
     */
    public ActorContext<T> getUnderlying() {
        return underlying;
    }

    // Convenience delegate methods for common ActorContext operations

    /**
     * Returns the ActorRef for this actor.
     *
     * @return This actor's reference
     */
    public SpringActorHandle<T> getSelf() {
        return new SpringActorHandle<>(underlying.getSystem().scheduler(), underlying.getSelf());
    }

    /**
     * Returns the logger for this actor.
     *
     * @return The SLF4J logger instance
     */
    public Logger getLog() {
        return underlying.getLog();
    }

    /**
     * Returns the scheduler for this actor system.
     *
     * @return The scheduler
     */
    public Scheduler getScheduler() {
        return underlying.getSystem().scheduler();
    }

    /**
     * Spawns a child actor with the given behavior and name.
     *
     * @param behavior The behavior for the child actor
     * @param name The name for the child actor
     * @param <M> The message type the child handles
     * @return Reference to the spawned child actor
     */
    public <M> ActorRef<M> spawn(Behavior<M> behavior, String name) {
        return underlying.spawn(behavior, name);
    }

    /**
     * Returns the actor path of this actor.
     *
     * @return The actor path
     */
    public ActorPath path() {
        return underlying.getSelf().path();
    }

    /**
     * Returns a child actor by name if it exists.
     *
     * @param name The name of the child actor
     * @return Optional containing the child actor reference, or empty if not found
     */
    public Optional<ActorRef<Void>> getChild(String name) {
        return underlying.getChild(name);
    }

    /**
     * Returns all child actors of this actor.
     *
     * @return List of child actor references
     */
    public List<ActorRef<Void>> getChildren() {
        return underlying.getChildren();
    }

    /**
     * Stops a child actor.
     *
     * @param child The child actor to stop
     */
    public void stop(ActorRef<?> child) {
        underlying.stop(child);
    }
}

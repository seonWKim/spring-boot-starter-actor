package io.github.seonwkim.core;

/**
 * Represents the context for an actor in the Spring Actor system.
 *
 * <p>This interface provides a way to identify actors uniquely within the actor system through the
 * {@link #actorId()} method. It can be extended to include additional context information specific
 * to different actor types.
 *
 * <p>The default implementation is {@code DefaultSpringActorContext}, which simply stores and
 * returns an actor ID. Custom implementations can add additional context information as needed,
 * such as in the chat example's {@code UserActorContext} which includes a WebSocketSession.
 *
 * <p>This context is used when spawning and stopping actors through the {@code SpringActorSystem},
 * and is passed to actor factory methods during creation.
 *
 * <p><b>Hierarchical Supervision:</b> For spawning child actors with Spring dependency injection,
 * use the {@link FrameworkCommands.SpawnChild} command when your actor's command interface extends
 * {@link FrameworkCommand}. This provides a clean, message-based API for hierarchical actor trees.
 *
 * @see FrameworkCommands.SpawnChild
 * @see FrameworkCommand
 */
public interface SpringActorContext {

    /**
     * Returns the unique identifier for this actor.
     *
     * <p>This ID is used to identify the actor within the actor system and should be unique for each
     * actor of the same type. It is used when spawning, addressing, and stopping actors.
     *
     * @return the unique actor identifier
     */
    String actorId();

    /**
     * Returns the actor type registry for creating Spring-managed child actors.
     *
     * <p>This registry is automatically injected by the framework when actors are created.
     * Default implementations return null. The framework ensures a non-null registry is available
     * when actors are spawned through SpringActorSystem.
     *
     * <p><b>Internal use only:</b> This method is used internally by the framework to enable
     * {@link FrameworkCommands.SpawnChild} functionality. Application code should not need to
     * access the registry directly.
     *
     * @return the actor type registry, or null if not yet injected
     */
    default ActorTypeRegistry registry() {
        return null;
    }
}

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
}

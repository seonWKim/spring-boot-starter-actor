package io.github.seonwkim.core;

import javax.annotation.Nullable;

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
public abstract class SpringActorContext {

    @Nullable private ActorTypeRegistry registry;
    private MdcConfig mdcConfig = MdcConfig.empty();

    /**
     * Returns the unique identifier for this actor.
     *
     * <p>This ID is used to identify the actor within the actor system and should be unique for each
     * actor of the same type. It is used when spawning, addressing, and stopping actors.
     *
     * @return the unique actor identifier
     */
    public abstract String actorId();

    @Nullable public ActorTypeRegistry registry() {
        return registry;
    }

    public void setRegistry(ActorTypeRegistry registry) {
        this.registry = registry;
    }

    /**
     * Returns the MDC configuration for this actor context.
     * Static MDC values configured here will be available in the actor's logs.
     *
     * @return the MDC configuration
     */
    public MdcConfig mdcConfig() {
        return mdcConfig;
    }

    /**
     * Sets the MDC configuration for this actor context.
     *
     * @param mdcConfig the MDC configuration
     */
    public void setMdcConfig(MdcConfig mdcConfig) {
        this.mdcConfig = mdcConfig != null ? mdcConfig : MdcConfig.empty();
    }
}

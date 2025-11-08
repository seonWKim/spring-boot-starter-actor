package io.github.seonwkim.core;

import javax.annotation.Nullable;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;

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
 * <p><b>Event Publishing:</b> Actors can publish Spring application events using the
 * {@link #publishApplicationEvent(ApplicationEvent)} method when an ApplicationEventPublisher
 * is configured. This enables integration between the actor system and Spring's event infrastructure.
 *
 * @see FrameworkCommands.SpawnChild
 * @see FrameworkCommand
 */
public abstract class SpringActorContext {

    @Nullable private ActorTypeRegistry registry;

    private MdcConfig mdcConfig = MdcConfig.empty();

    @Nullable private ApplicationEventPublisher eventPublisher;

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

    /**
     * Returns the ApplicationEventPublisher for publishing Spring application events.
     *
     * @return the event publisher, or null if not configured
     */
    @Nullable public ApplicationEventPublisher eventPublisher() {
        return eventPublisher;
    }

    /**
     * Sets the ApplicationEventPublisher for this actor context.
     *
     * @param eventPublisher the event publisher
     */
    public void setEventPublisher(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    /**
     * Publishes an application event to Spring's event infrastructure.
     *
     * <p>This method allows actors to publish Spring ApplicationEvents, enabling
     * integration between the actor system and Spring's event listeners.
     *
     * <p>Example usage:
     * <pre>
     * {@code
     * @Override
     * public SpringActorBehavior<Command> create(SpringActorContext ctx) {
     *     return SpringActorBehavior.builder(Command.class, ctx)
     *         .onMessage(OrderCreated.class, (context, msg) -> {
     *             ctx.publishApplicationEvent(
     *                 new OrderCreatedEvent(msg.orderId())
     *             );
     *             return Behaviors.same();
     *         })
     *         .build();
     * }
     * }
     * </pre>
     *
     * @param event the application event to publish
     * @throws IllegalStateException if no ApplicationEventPublisher is configured
     */
    public void publishApplicationEvent(ApplicationEvent event) {
        if (eventPublisher == null) {
            throw new IllegalStateException(
                    "ApplicationEventPublisher not configured for this actor context. "
                            + "Ensure event bridge auto-configuration is enabled.");
        }
        eventPublisher.publishEvent(event);
    }
}

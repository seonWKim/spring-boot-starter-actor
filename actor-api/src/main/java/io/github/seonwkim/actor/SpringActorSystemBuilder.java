package io.github.seonwkim.actor;

import java.util.Map;
import org.springframework.context.ApplicationEventPublisher;

/**
 * Builder interface for creating SpringActorSystem instances. This interface provides methods for
 * configuring the actor system before building it.
 */
public interface SpringActorSystemBuilder {

    /**
     * Sets the configuration for the actor system.
     *
     * @param config The configuration map
     * @return This builder for method chaining
     */
    SpringActorSystemBuilder withConfig(Map<String, Object> config);

    /**
     * Sets the application event publisher for the actor system. This is required for cluster mode to
     * publish cluster events.
     *
     * @param applicationEventPublisher The Spring application event publisher
     * @return This builder for method chaining
     */
    SpringActorSystemBuilder withApplicationEventPublisher(ApplicationEventPublisher applicationEventPublisher);

    /**
     * Builds a SpringActorSystem with the configured settings.
     *
     * @return A new SpringActorSystem
     */
    SpringActorSystem build();
}

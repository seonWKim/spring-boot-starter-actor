package io.github.seonwkim.core;

import java.util.Map;
import org.springframework.context.ApplicationEventPublisher;

/**
 * Builder interface for creating SpringActorSystem instances. This interface provides methods for
 * configuring the actor system before building it.
 *
 * <p>Actor registries (ActorTypeRegistry and ShardedActorRegistry) are now static utilities
 * and are automatically populated via BeanPostProcessor, so they no longer need to be passed to the builder.
 */
public interface SpringActorSystemBuilder {

    /**
     * Sets the root guardian supplier for the actor system.
     *
     * @param supplier The root guardian supplier wrapper
     * @return This builder for method chaining
     */
    SpringActorSystemBuilder withRootGuardianSupplier(RootGuardianSupplierWrapper supplier);

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
     * Uses the static ShardedActorRegistry which is populated by BeanPostProcessor.
     *
     * @return A new SpringActorSystem
     */
    SpringActorSystem build();
}

package io.github.seonwkim.core.event;

import io.github.seonwkim.core.ActorConfiguration;
import io.github.seonwkim.core.SpringActorContext;
import io.github.seonwkim.core.SpringActorSystem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Auto-configuration for Spring Events Bridge.
 *
 * <p>This configuration enables integration between Spring application events
 * and the actor system by:
 * <ul>
 *   <li>Enabling actors to publish Spring application events</li>
 *   <li>Enabling Spring event listeners to send messages to actors via {@link SendToActor}</li>
 * </ul>
 *
 * <p>The configuration is automatically enabled when:
 * <ul>
 *   <li>{@link SpringActorSystem} is on the classpath</li>
 *   <li>The property {@code actor.event-bridge.enabled} is not explicitly set to false</li>
 * </ul>
 *
 * <p>To disable event bridge, set the following property:
 * <pre>
 * actor.event-bridge.enabled=false
 * </pre>
 */
@Configuration
@AutoConfigureAfter(ActorConfiguration.class)
@ConditionalOnClass(SpringActorSystem.class)
@ConditionalOnProperty(prefix = "actor.event-bridge", name = "enabled", havingValue = "true", matchIfMissing = true)
public class EventBridgeAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(EventBridgeAutoConfiguration.class);

    public EventBridgeAutoConfiguration() {
        log.info("Spring Events Bridge auto-configuration enabled");
    }

    /**
     * Creates a bean post processor to detect and process {@link SendToActor} annotations.
     *
     * @param actorSystem the actor system
     * @return the bean post processor
     */
    @Bean
    public SendToActorBeanPostProcessor sendToActorBeanPostProcessor(SpringActorSystem actorSystem) {
        log.info("Enabling @SendToActor annotation support");
        return new SendToActorBeanPostProcessor(actorSystem);
    }

    /**
     * Creates an event listener factory for @SendToActor methods.
     *
     * @param processor the SendToActorBeanPostProcessor
     * @return the event listener factory
     */
    @Bean
    public SendToActorEventListenerFactory sendToActorEventListenerFactory(
            SendToActorBeanPostProcessor processor) {
        log.info("Registering SendToActorEventListenerFactory");
        return new SendToActorEventListenerFactory(processor);
    }

    /**
     * Creates a bean that configures the ApplicationEventPublisher for actor contexts.
     *
     * @param actorSystem the actor system
     * @param eventPublisher the Spring event publisher
     * @return the configurator bean
     */
    @Bean
    public ActorContextEventPublisherConfigurator actorContextEventPublisherConfigurator(
            SpringActorSystem actorSystem, ApplicationEventPublisher eventPublisher) {
        log.info("Configuring ApplicationEventPublisher for actor contexts");
        return new ActorContextEventPublisherConfigurator(actorSystem, eventPublisher);
    }

    /**
     * Configurator that sets the ApplicationEventPublisher on SpringActorContext instances.
     */
    public static class ActorContextEventPublisherConfigurator {
        private final SpringActorSystem actorSystem;
        private final ApplicationEventPublisher eventPublisher;

        public ActorContextEventPublisherConfigurator(
                SpringActorSystem actorSystem, ApplicationEventPublisher eventPublisher) {
            this.actorSystem = actorSystem;
            this.eventPublisher = eventPublisher;
            configureEventPublisher();
        }

        private void configureEventPublisher() {
            // Set the event publisher on the actor system so it can inject it
            // into SpringActorContext instances when they are created
            actorSystem.setDefaultEventPublisher(eventPublisher);
            log.debug("Configured default ApplicationEventPublisher for actor system");
        }
    }
}

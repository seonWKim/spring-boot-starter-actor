package io.github.seonwkim.actor.autoconfigure;

import io.github.seonwkim.actor.SpringActorSystem;
import io.github.seonwkim.actor.SpringActorSystemBuilder;
import io.github.seonwkim.actor.SpringActorWithContext;
import io.github.seonwkim.actor.internal.ActorTypeRegistry;
import io.github.seonwkim.actor.internal.DefaultSpringActorSystemBuilder;
import io.github.seonwkim.actor.internal.PekkoReceptionist;
import io.github.seonwkim.actor.internal.PekkoTopicManager;
import io.github.seonwkim.actor.internal.RootGuardian;
import io.github.seonwkim.actor.internal.RootGuardianSupplierWrapper;
import io.github.seonwkim.actor.receptionist.SpringReceptionist;
import io.github.seonwkim.actor.topic.SpringTopicManager;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * Auto-configuration for the Spring Boot Actor System.
 *
 * <p><b>Initialization Flow:</b>
 * <ol>
 *   <li>Spring scans and creates actor beans (with DI)</li>
 *   <li>{@link #actorRegistrationBeanPostProcessor()} registers actors in static registries</li>
 *   <li>{@link #actorSystem(SpringActorSystemBuilder)} builds the actor system</li>
 *   <li>In cluster mode, {@code actor-cluster} registers sharded actors once all beans exist</li>
 * </ol>
 *
 * <p><b>Key Design:</b> Uses the static {@link ActorTypeRegistry}
 * to avoid circular dependencies between actor system components.
 */
@Configuration
@EnableConfigurationProperties(ActorProperties.class)
public class ActorConfiguration {

    private static final Logger log = LoggerFactory.getLogger(ActorConfiguration.class);

    // ==================================================================================
    // PHASE 1: Actor Registration (BeanPostProcessor)
    // ==================================================================================

    /**
     * Registers actor beans in static registries as they are initialized.
     *
     * <p>Runs automatically after each bean initialization to register:
     * <ul>
     *   <li>{@code SpringActor} → {@link ActorTypeRegistry}</li>
     * </ul>
     *
     * <p><b>Why BeanPostProcessor?</b>
     * <ul>
     *   <li>Automatic - no manual registration needed</li>
     *   <li>Runs after bean creation - Spring DI already complete</li>
     *   <li>Avoids circular dependencies - registries are static</li>
     * </ul>
     */
    @Bean
    public BeanPostProcessor actorRegistrationBeanPostProcessor() {
        return new BeanPostProcessor() {
            @Override
            @SuppressWarnings({"rawtypes", "unchecked"})
            public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
                if (bean instanceof SpringActorWithContext) {
                    registerActor((SpringActorWithContext) bean);
                }
                return bean;
            }

            private void registerActor(SpringActorWithContext actor) {
                ActorTypeRegistry.registerInternal(actor.getClass(), actorContext -> {
                    try {
                        return actor.create(actorContext);
                    } catch (Exception e) {
                        throw new RuntimeException(
                                "Failed to create actor behavior for "
                                        + actor.getClass().getName(),
                                e);
                    }
                });
            }
        };
    }

    // ==================================================================================
    // PHASE 2: Actor System Construction
    // ==================================================================================

    /**
     * Creates the root guardian that manages all actors.
     * Uses static {@link ActorTypeRegistry} to avoid circular dependencies.
     */
    @Bean
    @ConditionalOnMissingBean(RootGuardianSupplierWrapper.class)
    public RootGuardianSupplierWrapper rootGuardianSupplierWrapper() {
        return new RootGuardianSupplierWrapper(RootGuardian::create);
    }

    /**
     * Builds the actor system configuration.
     *
     * <p>Dependencies:
     * <ul>
     *   <li>{@link ActorProperties} and the raw {@code spring.actor.pekko.*} keys - Pekko configuration</li>
     *   <li>{@link RootGuardianSupplierWrapper} - Actor hierarchy root</li>
     *   <li>{@link ApplicationEventPublisher} - Spring event integration</li>
     * </ul>
     */
    @Bean
    @ConditionalOnMissingBean(SpringActorSystemBuilder.class)
    public SpringActorSystemBuilder actorSystemBuilder(
            ActorProperties actorProperties,
            Environment environment,
            RootGuardianSupplierWrapper rootGuardianSupplierWrapper,
            ApplicationEventPublisher applicationEventPublisher,
            BeanFactory beanFactory) {
        Map<String, Object> springActor = Binder.get(environment)
                .bind("spring.actor", Bindable.mapOf(String.class, Object.class))
                .orElse(Map.of());
        return new DefaultSpringActorSystemBuilder()
                .withRootGuardianSupplier(rootGuardianSupplierWrapper)
                .withBeanFactory(beanFactory)
                .withConfig(actorProperties.toPekkoConfig(springActor))
                .withApplicationEventPublisher(applicationEventPublisher);
    }

    /**
     * Creates the main actor system.
     *
     * <p>At this point, actors are registered in static registries
     * but cluster sharding is not yet initialized.
     */
    @Bean
    @ConditionalOnMissingBean(SpringActorSystem.class)
    public SpringActorSystem actorSystem(SpringActorSystemBuilder builder) {
        return builder.build();
    }

    /**
     * Runs Pekko coordinated shutdown when the Spring context closes, after the web server has
     * stopped and before the beans the actors use are destroyed. Waits at most
     * {@code spring.actor.shutdown.timeout} (default 20s).
     */
    @Bean
    @ConditionalOnMissingBean(ActorSystemShutdownLifecycle.class)
    public ActorSystemShutdownLifecycle actorSystemShutdownLifecycle(
            SpringActorSystem actorSystem, ActorProperties actorProperties, ConfigurableApplicationContext context) {
        boolean exitProcess = actorProperties.getShutdown().isExitProcess();
        return new ActorSystemShutdownLifecycle(
                actorSystem, actorProperties.getShutdown().getTimeout(), () -> {
                    log.error(
                            "The actor system terminated while the application was running: closing the application context"
                                    + (exitProcess ? " and exiting with status 1" : ""));
                    int status = SpringApplication.exit(context, () -> 1);
                    if (exitProcess) {
                        System.exit(status);
                    }
                });
    }

    // ==================================================================================
    // Additional Components
    // ==================================================================================

    /**
     * Provides pub/sub topic management for actors.
     */
    @Bean
    @ConditionalOnMissingBean(SpringTopicManager.class)
    public SpringTopicManager topicManager(SpringActorSystem actorSystem) {
        return new PekkoTopicManager(actorSystem);
    }

    /**
     * Provides actor discovery by service key through Pekko's Receptionist.
     */
    @Bean
    @ConditionalOnMissingBean(SpringReceptionist.class)
    public SpringReceptionist springReceptionist(SpringActorSystem actorSystem) {
        return new PekkoReceptionist(actorSystem);
    }
}

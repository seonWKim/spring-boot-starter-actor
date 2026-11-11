package io.github.seonwkim.actor.autoconfigure;

import io.github.seonwkim.actor.SpringActorSystem;
import io.github.seonwkim.actor.SpringActorSystemBuilder;
import io.github.seonwkim.actor.SpringActorWithContext;
import io.github.seonwkim.actor.delivery.SpringReliableDelivery;
import io.github.seonwkim.actor.internal.ActorTypeRegistry;
import io.github.seonwkim.actor.internal.DefaultSpringActorSystemBuilder;
import io.github.seonwkim.actor.internal.PekkoReceptionist;
import io.github.seonwkim.actor.internal.PekkoTopicManager;
import io.github.seonwkim.actor.internal.RootGuardian;
import io.github.seonwkim.actor.internal.RootGuardianSupplierWrapper;
import io.github.seonwkim.actor.internal.delivery.PekkoReliableDelivery;
import io.github.seonwkim.actor.receptionist.SpringReceptionist;
import io.github.seonwkim.actor.topic.SpringTopicManager;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

/**
 * Auto-configuration for the Spring Boot Actor System.
 *
 * <p><b>Initialization Flow:</b>
 * <ol>
 *   <li>Spring scans and creates actor beans (with DI)</li>
 *   <li>{@link #actorRegistrationBeanPostProcessor} registers actors in this context's {@link ActorTypeRegistry}</li>
 *   <li>{@link #actorSystem(SpringActorSystemBuilder)} builds the actor system</li>
 *   <li>In cluster mode, {@code actor-cluster} registers sharded actors once all beans exist</li>
 * </ol>
 *
 * <p><b>Key Design:</b> Actors are created from the {@link ActorTypeRegistry} rather than looked up as
 * beans, which avoids circular dependencies between actor beans and the actor system. The registry belongs
 * to this context, so several contexts in one JVM keep their actors apart.
 */
@AutoConfiguration
@ConditionalOnActorEnabled
@EnableConfigurationProperties(ActorProperties.class)
public class ActorConfiguration {

    private static final Logger log = LoggerFactory.getLogger(ActorConfiguration.class);

    // ==================================================================================
    // PHASE 1: Actor Registration (BeanPostProcessor)
    // ==================================================================================

    /**
     * Registers actor beans in this context's registry as they are initialized.
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
     *   <li>Avoids circular dependencies - actors are found in the registry, not looked up as beans</li>
     * </ul>
     */
    @Bean
    public static BeanPostProcessor actorRegistrationBeanPostProcessor(ActorTypeRegistry actorTypeRegistry) {
        return new BeanPostProcessor() {
            @Override
            @SuppressWarnings("rawtypes")
            public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
                if (bean instanceof SpringActorWithContext actor) {
                    actorTypeRegistry.registerBean(actor);
                }
                return bean;
            }
        };
    }

    // ==================================================================================
    // PHASE 2: Actor System Construction
    // ==================================================================================

    /**
     * This context's actor beans by class; the actor system built here carries it. It creates an
     * actor bean on first spawn when Spring hasn't yet, so a bean may spawn in its constructor.
     */
    @Bean
    public static ActorTypeRegistry actorTypeRegistry(BeanFactory beanFactory) {
        return new ActorTypeRegistry(beanFactory);
    }

    /** Creates the root guardian that manages all actors. */
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
                .withConfig(actorProperties.toPekkoConfig(
                        springActor, environment.getProperty("spring.threads.virtual.enabled", Boolean.class, false)))
                .withApplicationEventPublisher(applicationEventPublisher);
    }

    /**
     * Creates the main actor system.
     *
     * <p>At this point, actors are registered in this context's registries
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

    /**
     * Starts senders that deliver at least once (reliable delivery), retrying as
     * {@code spring.actor.delivery.retry.*} says.
     */
    @Bean
    @ConditionalOnMissingBean(SpringReliableDelivery.class)
    public SpringReliableDelivery springReliableDelivery(
            SpringActorSystem actorSystem, ActorProperties actorProperties) {
        return new PekkoReliableDelivery(
                actorSystem, actorProperties.getDelivery().getRetry().toRetryPolicy());
    }
}

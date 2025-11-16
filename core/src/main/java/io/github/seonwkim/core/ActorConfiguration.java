package io.github.seonwkim.core;

import io.github.seonwkim.core.impl.DefaultSpringActorSystemBuilder;
import io.github.seonwkim.core.shard.ShardedActorRegistry;
import io.github.seonwkim.core.shard.SpringShardedActor;
import io.github.seonwkim.core.topic.SpringTopicManager;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.ApplicationEventPublisher;
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
 *   <li>{@link #clusterShardingInitializer(SpringActorSystem)} initializes cluster sharding</li>
 * </ol>
 *
 * <p><b>Key Design:</b> Uses static registries ({@link ActorTypeRegistry}, {@link ShardedActorRegistry})
 * to avoid circular dependencies between actor system components.
 */
@Configuration
public class ActorConfiguration {

    // ==================================================================================
    // PHASE 1: Actor Registration (BeanPostProcessor)
    // ==================================================================================

    /**
     * Registers actor beans in static registries as they are initialized.
     *
     * <p>Runs automatically after each bean initialization to register:
     * <ul>
     *   <li>{@link SpringActor} → {@link ActorTypeRegistry}</li>
     *   <li>{@link SpringShardedActor} → {@link ShardedActorRegistry}</li>
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
                if (bean instanceof SpringShardedActor) {
                    registerShardedActor((SpringShardedActor) bean);
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

            private void registerShardedActor(SpringShardedActor shardedActor) {
                ShardedActorRegistry.register(shardedActor);
            }
        };
    }

    // ==================================================================================
    // PHASE 2: Actor System Construction
    // ==================================================================================

    /**
     * Provides configuration properties for the actor system.
     */
    @Bean
    @ConditionalOnMissingBean(ActorProperties.class)
    public ActorProperties actorProperties(Environment environment) {
        ActorProperties properties = new ActorProperties();
        properties.setEnvironment(environment);
        return properties;
    }

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
     *   <li>{@link ActorProperties} - Pekko configuration</li>
     *   <li>{@link RootGuardianSupplierWrapper} - Actor hierarchy root</li>
     *   <li>{@link ApplicationEventPublisher} - Spring event integration</li>
     * </ul>
     */
    @Bean
    @ConditionalOnMissingBean(SpringActorSystemBuilder.class)
    public SpringActorSystemBuilder actorSystemBuilder(
            ActorProperties actorProperties,
            RootGuardianSupplierWrapper rootGuardianSupplierWrapper,
            ApplicationEventPublisher applicationEventPublisher) {
        return new DefaultSpringActorSystemBuilder()
                .withConfig(actorProperties.getConfig())
                .withRootGuardianSupplier(rootGuardianSupplierWrapper)
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

    // ==================================================================================
    // PHASE 3: Post-Initialization (SmartInitializingSingleton)
    // ==================================================================================

    /**
     * Initializes cluster sharding after all actors are registered.
     *
     * <p><b>Why SmartInitializingSingleton?</b>
     * <ul>
     *   <li>Runs after ALL singleton beans are created</li>
     *   <li>Ensures all sharded actors are registered before init</li>
     *   <li>Perfect timing for cluster sharding setup</li>
     * </ul>
     */
    @Bean
    public SmartInitializingSingleton clusterShardingInitializer(SpringActorSystem actorSystem) {
        return () -> {
            if (actorSystem.isClusterMode()) {
                actorSystem.initializeClusterSharding();
            }
        };
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
        return new SpringTopicManager(actorSystem);
    }
}

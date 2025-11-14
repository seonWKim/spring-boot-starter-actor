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

@Configuration
public class ActorConfiguration {

    /**
     * BeanPostProcessor that automatically registers actors in static registries.
     * This runs after each bean is initialized, detecting and registering:
     * - SpringActorWithContext beans in ActorTypeRegistry
     * - SpringShardedActor beans in ShardedActorRegistry
     *
     * <p>This approach eliminates circular dependencies by:
     * 1. Using static registries (no Spring bean dependencies)
     * 2. Automatic registration via BeanPostProcessor (no explicit bean wiring)
     */
    @Bean
    public BeanPostProcessor actorRegistrationBeanPostProcessor() {
        return new BeanPostProcessor() {
            @Override
            @SuppressWarnings({"rawtypes", "unchecked"})
            public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
                // Register SpringActorWithContext implementations (includes SpringActor)
                if (bean instanceof SpringActorWithContext) {
                    SpringActorWithContext actor = (SpringActorWithContext) bean;
                    ActorTypeRegistry.registerInternal(actor.getClass(), actorContext -> {
                        try {
                            return actor.create(actorContext);
                        } catch (Exception e) {
                            throw new RuntimeException(
                                    "Failed to invoke create() on "
                                            + actor.getClass().getName(),
                                    e);
                        }
                    });
                }

                // Register SpringShardedActor implementations
                if (bean instanceof SpringShardedActor) {
                    SpringShardedActor shardedActor = (SpringShardedActor) bean;
                    ShardedActorRegistry.register(shardedActor);
                }

                return bean;
            }
        };
    }

    /**
     * Creates a RootGuardianSupplierWrapper bean that supplies a RootGuardian behavior.
     * Uses the static ActorTypeRegistry which is populated by the BeanPostProcessor.
     *
     * @return A RootGuardianSupplierWrapper
     */
    @Bean
    @ConditionalOnMissingBean(RootGuardianSupplierWrapper.class)
    public RootGuardianSupplierWrapper rootGuardianSupplierWrapper() {
        return new RootGuardianSupplierWrapper(RootGuardian::create);
    }

    /**
     * Creates a SpringActorSystemBuilder bean with the given properties, root guardian supplier,
     * and application event publisher. Registries are now static and populated by BeanPostProcessor.
     *
     * @param properties The Pekko properties
     * @param rootGuardianSupplierWrapper The root guardian supplier wrapper
     * @param applicationEventPublisher The application event publisher
     * @return A SpringActorSystemBuilder
     */
    @Bean
    @ConditionalOnMissingBean(SpringActorSystemBuilder.class)
    public SpringActorSystemBuilder actorSystemBuilder(
            ActorProperties properties,
            RootGuardianSupplierWrapper rootGuardianSupplierWrapper,
            ApplicationEventPublisher applicationEventPublisher) {
        return new DefaultSpringActorSystemBuilder()
                .withConfig(properties.getConfig())
                .withRootGuardianSupplier(rootGuardianSupplierWrapper)
                .withApplicationEventPublisher(applicationEventPublisher);
    }

    /**
     * Creates a SpringActorSystem bean using the given builder.
     *
     * @param builder The SpringActorSystemBuilder to use
     * @return A SpringActorSystem
     */
    @Bean
    @ConditionalOnMissingBean(SpringActorSystem.class)
    public SpringActorSystem actorSystem(SpringActorSystemBuilder builder) {
        return builder.build();
    }

    /**
     * Initializes cluster sharding after all singleton beans are created.
     * This ensures all sharded actor beans are registered in the static registry
     * before cluster sharding tries to initialize them.
     */
    @Bean
    public SmartInitializingSingleton clusterShardingInitializer(SpringActorSystem actorSystem) {
        return () -> {
            if (actorSystem.isClusterMode()) {
                actorSystem.initializeClusterSharding();
            }
        };
    }

    @Bean
    @ConditionalOnMissingBean(SpringTopicManager.class)
    public SpringTopicManager topicManager(SpringActorSystem actorSystem) {
        return new SpringTopicManager(actorSystem);
    }

    /**
     * Creates a PekkoProperties bean with the given environment. This bean provides configuration
     * properties for the Pekko actor system.
     *
     * @param environment The Spring environment
     * @return A PekkoProperties instance
     */
    @Bean
    @ConditionalOnMissingBean(ActorProperties.class)
    public ActorProperties pekkoProperties(Environment environment) {
        ActorProperties properties = new ActorProperties();
        properties.setEnvironment(environment); // manually inject Environment
        return properties;
    }
}

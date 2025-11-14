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
     * Automatically registers actor beans in static registries after initialization.
     * Eliminates circular dependencies by using static registries instead of bean wiring.
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
     * Creates the root guardian supplier using the static ActorTypeRegistry.
     */
    @Bean
    @ConditionalOnMissingBean(RootGuardianSupplierWrapper.class)
    public RootGuardianSupplierWrapper rootGuardianSupplierWrapper() {
        return new RootGuardianSupplierWrapper(RootGuardian::create);
    }

    /**
     * Creates a SpringActorSystemBuilder with configuration and event publishing support.
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
     * Creates the main SpringActorSystem from the builder.
     */
    @Bean
    @ConditionalOnMissingBean(SpringActorSystem.class)
    public SpringActorSystem actorSystem(SpringActorSystemBuilder builder) {
        return builder.build();
    }

    /**
     * Initializes cluster sharding after all actors are registered.
     */
    @Bean
    public SmartInitializingSingleton clusterShardingInitializer(SpringActorSystem actorSystem) {
        return () -> {
            if (actorSystem.isClusterMode()) {
                actorSystem.initializeClusterSharding();
            }
        };
    }

    /**
     * Creates the topic manager for pub/sub messaging.
     */
    @Bean
    @ConditionalOnMissingBean(SpringTopicManager.class)
    public SpringTopicManager topicManager(SpringActorSystem actorSystem) {
        return new SpringTopicManager(actorSystem);
    }

    /**
     * Creates actor configuration properties from Spring environment.
     */
    @Bean
    @ConditionalOnMissingBean(ActorProperties.class)
    public ActorProperties pekkoProperties(Environment environment) {
        ActorProperties properties = new ActorProperties();
        properties.setEnvironment(environment); // manually inject Environment
        return properties;
    }
}

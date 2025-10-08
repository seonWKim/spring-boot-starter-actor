package io.github.seonwkim.core;

import io.github.seonwkim.core.impl.DefaultSpringActorSystemBuilder;
import io.github.seonwkim.core.shard.ShardedActor;
import io.github.seonwkim.core.shard.ShardedActorRegistry;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration
public class ActorConfiguration {
    /**
     * Creates a SpringActorSystemBuilder bean with the given properties, root guardian supplier,
     * application event publisher, and sharded actor registry.
     *
     * @param properties The Pekko properties
     * @param rootGuardianSupplierWrapper The root guardian supplier wrapper
     * @param applicationEventPublisher The application event publisher
     * @param shardedActorRegistry The sharded actor registry
     * @return A SpringActorSystemBuilder
     */
    @Bean
    @ConditionalOnMissingBean(SpringActorSystemBuilder.class)
    public SpringActorSystemBuilder actorSystemBuilder(
            ActorProperties properties,
            RootGuardianSupplierWrapper rootGuardianSupplierWrapper,
            ApplicationEventPublisher applicationEventPublisher,
            ShardedActorRegistry shardedActorRegistry) {
        return new DefaultSpringActorSystemBuilder()
                .withConfig(properties.getConfig())
                .withRootGuardianSupplier(rootGuardianSupplierWrapper)
                .withApplicationEventPublisher(applicationEventPublisher)
                .withShardedActorRegistry(shardedActorRegistry);
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
     * Creates a RootGuardianSupplierWrapper bean that supplies a RootGuardian behavior.
     *
     * @param actorTypeRegistry The ActorTypeRegistry to use for creating the RootGuardian
     * @return A RootGuardianSupplierWrapper
     */
    @Bean
    @ConditionalOnMissingBean(RootGuardianSupplierWrapper.class)
    public RootGuardianSupplierWrapper rootGuardianSupplierWrapper(ActorTypeRegistry actorTypeRegistry) {
        return new RootGuardianSupplierWrapper(() -> RootGuardian.create(actorTypeRegistry));
    }

    /**
     * Creates an ActorTypeRegistry bean and registers all SpringActor beans in the application
     * context. For each SpringActor, it registers the create method from the SpringActor interface.
     *
     * @param context The Spring application context
     * @return An ActorTypeRegistry with all SpringActor beans registered
     */
    @Bean
    @ConditionalOnMissingBean(ActorTypeRegistry.class)
    public ActorTypeRegistry actorTypeRegistry(ApplicationContext context) {
        ActorTypeRegistry registry = new ActorTypeRegistry();
        Map<String, SpringActor> actorBeans = context.getBeansOfType(SpringActor.class);

        for (SpringActor actorBean : actorBeans.values()) {
            registry.register(actorBean.getClass(), actorContext -> {
                try {
                    return actorBean.create(actorContext);
                } catch (Exception e) {
                    throw new RuntimeException(
                            "Failed to invoke create(id) on "
                                    + actorBean.getClass().getName(),
                            e);
                }
            });
        }

        return registry;
    }

    /**
     * Creates a ShardedActorRegistry bean and registers all ShardedActor beans in the application
     * context.
     *
     * @param ctx The Spring application context
     * @return A ShardedActorRegistry with all ShardedActor beans registered
     */
    @Bean
    @ConditionalOnMissingBean(ShardedActorRegistry.class)
    public ShardedActorRegistry shardedActorRegistry(ApplicationContext ctx) {
        ShardedActorRegistry registry = new ShardedActorRegistry();
        Map<String, ShardedActor> beans = ctx.getBeansOfType(ShardedActor.class);
        beans.values().forEach(registry::register);
        return registry;
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

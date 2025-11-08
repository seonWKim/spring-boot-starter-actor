package io.github.seonwkim.core;

import io.github.seonwkim.core.impl.DefaultSpringActorSystemBuilder;
import io.github.seonwkim.core.shard.ShardedActorRegistry;
import io.github.seonwkim.core.shard.SpringShardedActor;
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
     * Creates an ActorTypeRegistry bean and registers all SpringActorWithContext beans in the
     * application context. This includes both SpringActor and SpringActorWithContext implementations.
     *
     * @param context The Spring application context
     * @return An ActorTypeRegistry with all SpringActorWithContext beans registered
     */
    @Bean
    @ConditionalOnMissingBean(ActorTypeRegistry.class)
    @SuppressWarnings({"rawtypes", "unchecked"})
    public ActorTypeRegistry actorTypeRegistry(ApplicationContext context) {
        ActorTypeRegistry registry = new ActorTypeRegistry();
        // SpringActor extends SpringActorWithContext, so this gets both
        Map<String, SpringActorWithContext> actorBeans = context.getBeansOfType(SpringActorWithContext.class);

        for (SpringActorWithContext actorBean : actorBeans.values()) {
            // Check if this is a router actor
            if (actorBean instanceof io.github.seonwkim.core.router.SpringRouterActor) {
                io.github.seonwkim.core.router.SpringRouterActor routerBean =
                        (io.github.seonwkim.core.router.SpringRouterActor) actorBean;
                registry.registerInternal(actorBean.getClass(), actorContext -> {
                    try {
                        // Create router behavior
                        io.github.seonwkim.core.router.SpringRouterBehavior routerBehavior =
                                routerBean.create(actorContext);

                        // Convert to SpringActorBehavior with worker factory
                        return routerBehavior.toSpringActorBehavior(actorContext, (SpringActorContext workerContext) -> {
                            // Create worker behavior through registry
                            SpringActorBehavior workerSpringBehavior =
                                    registry.createTypedBehavior(routerBehavior.getWorkerClass(), workerContext);
                            return workerSpringBehavior.asBehavior();
                        });
                    } catch (Exception e) {
                        throw new RuntimeException(
                                "Failed to create router behavior for "
                                        + actorBean.getClass().getName(),
                                e);
                    }
                });
            } else {
                // Regular actor
                registry.registerInternal(actorBean.getClass(), actorContext -> {
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
        }

        return registry;
    }

    /**
     * Creates a ShardedActorRegistry bean and registers all SpringShardedActor beans in the application
     * context.
     *
     * @param ctx The Spring application context
     * @return A ShardedActorRegistry with all SpringShardedActor beans registered
     */
    @Bean
    @ConditionalOnMissingBean(ShardedActorRegistry.class)
    public ShardedActorRegistry shardedActorRegistry(ApplicationContext ctx) {
        ShardedActorRegistry registry = new ShardedActorRegistry();
        Map<String, SpringShardedActor> beans = ctx.getBeansOfType(SpringShardedActor.class);
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

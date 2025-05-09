package io.github.seonwkim.core;

import java.lang.reflect.Method;
import java.util.Map;

import org.apache.pekko.actor.typed.Behavior;
import io.github.seonwkim.core.impl.DefaultSpringActorSystemBuilder;
import io.github.seonwkim.core.shard.ShardedActor;
import io.github.seonwkim.core.shard.ShardedActorRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * Spring Boot auto-configuration for Pekko actor system.
 * This class sets up the actor system when the property "spring.actor-enabled" is set to "true".
 * It provides beans for the actor system, actor system builder, root guardian, actor type registry,
 * and sharded actor registry.
 */
@Configuration
@EnableConfigurationProperties(PekkoProperties.class)
@ConditionalOnProperty(prefix = "spring", name = "actor-enabled", havingValue = "true")
@ComponentScan(basePackages = "io.github.seonwkim.core")
public class PekkoAutoConfiguration {

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
    @ConditionalOnMissingBean
    public SpringActorSystemBuilder actorSystemBuilder(
            PekkoProperties properties,
            RootGuardianSupplierWrapper rootGuardianSupplierWrapper,
            ApplicationEventPublisher applicationEventPublisher,
            ShardedActorRegistry shardedActorRegistry
            ) {
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
    @ConditionalOnMissingBean
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
    @ConditionalOnMissingBean
    public RootGuardianSupplierWrapper rootGuardianSupplierWrapper(ActorTypeRegistry actorTypeRegistry) {
        return new RootGuardianSupplierWrapper(() -> RootGuardian.create(actorTypeRegistry));
    }

    /**
     * Creates an ActorTypeRegistry bean and registers all SpringActor beans in the application context.
     * For each SpringActor, it finds the static create(String) method and registers it as a factory.
     *
     * @param context The Spring application context
     * @return An ActorTypeRegistry with all SpringActor beans registered
     * @throws IllegalStateException If a SpringActor doesn't have a valid static create(String) method
     */
    @Bean
    @ConditionalOnMissingBean
    public ActorTypeRegistry actorTypeRegistry(ApplicationContext context) {
        ActorTypeRegistry registry = new ActorTypeRegistry();
        Map<String, SpringActor> actorBeans = context.getBeansOfType(SpringActor.class);

        for (SpringActor actorBean : actorBeans.values()) {
            Class<?> actorClass = actorBean.getClass(); // likely a CGLIB proxy
            Class<?> targetClass = findTargetClass(actorClass);
            Class<?> commandClass = actorBean.commandClass();

            Method factoryMethod = findCreateMethod(targetClass);
            if (factoryMethod == null) {
                throw new IllegalStateException("No valid static create(String) method found in " + targetClass.getName());
            }

            registry.register(commandClass, id -> {
                try {
                    return (Behavior<?>) factoryMethod.invoke(null, id);
                } catch (Exception e) {
                    throw new RuntimeException("Failed to invoke create(id) on " + targetClass.getName(), e);
                }
            });
        }

        return registry;
    }


    private Method findCreateMethod(Class<?> clazz) {
        try {
            Method m = clazz.getMethod("create", String.class);
            if (!Behavior.class.isAssignableFrom(m.getReturnType())) return null;
            if (!java.lang.reflect.Modifier.isStatic(m.getModifiers())) return null;
            return m;
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    private Class<?> findTargetClass(Class<?> clazz) {
        // Handles CGLIB proxy classes by finding the original user-defined class
        while (clazz.getName().contains("$$")) {
            clazz = clazz.getSuperclass();
        }
        return clazz;
    }

    /**
     * Creates a ShardedActorRegistry bean and registers all ShardedActor beans in the application context.
     *
     * @param ctx The Spring application context
     * @return A ShardedActorRegistry with all ShardedActor beans registered
     */
    @Bean
    @ConditionalOnMissingBean
    public ShardedActorRegistry shardedActorRegistry(ApplicationContext ctx) {
        ShardedActorRegistry registry = new ShardedActorRegistry();
        Map<String, ShardedActor> beans = ctx.getBeansOfType(ShardedActor.class);
        beans.values().forEach(registry::register);
        return registry;
    }
}

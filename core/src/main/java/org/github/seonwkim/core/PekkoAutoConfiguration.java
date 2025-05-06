package org.github.seonwkim.core;

import java.lang.reflect.Method;
import java.util.Map;

import org.apache.pekko.actor.typed.Behavior;
import org.github.seonwkim.core.impl.DefaultSpringActorSystemBuilder;
import org.github.seonwkim.core.shard.ShardedActor;
import org.github.seonwkim.core.shard.ShardedActorRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(PekkoProperties.class)
@ConditionalOnProperty(prefix = "actor.pekko", name = "enabled", havingValue = "true")
@ComponentScan(basePackages = "org.github.seonwkim.core")
public class PekkoAutoConfiguration {

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
                .withApplicationEventPublisher(applicationEventPublisher);
    }

    @Bean
    @ConditionalOnMissingBean
    public SpringActorSystem actorSystem(SpringActorSystemBuilder builder) {
        return builder.build();
    }

    @Bean
    @ConditionalOnMissingBean
    public RootGuardianSupplierWrapper rootGuardianSupplierWrapper(ActorTypeRegistry actorTypeRegistry) {
        return new RootGuardianSupplierWrapper(() -> RootGuardian.create(actorTypeRegistry));
    }

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

    @Bean
    @ConditionalOnMissingBean
    public ShardedActorRegistry shardedActorRegistry(ApplicationContext ctx) {
        ShardedActorRegistry registry = new ShardedActorRegistry();
        Map<String, ShardedActor> beans = ctx.getBeansOfType(ShardedActor.class);
        beans.values().forEach(registry::register);
        return registry;
    }

}

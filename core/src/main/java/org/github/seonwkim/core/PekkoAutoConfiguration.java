package org.github.seonwkim.core;

import java.lang.reflect.Method;
import java.util.Map;

import org.apache.pekko.actor.typed.Behavior;
import org.github.seonwkim.core.impl.DefaultSpringActorSystemBuilder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.AnnotationUtils;

@Configuration
@EnableConfigurationProperties(PekkoProperties.class)
@ConditionalOnProperty(prefix = "actor.pekko", name = "enabled", havingValue = "true")
@ComponentScan(basePackages = "org.github.seonwkim.core")
public class PekkoAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public SpringActorSystemBuilder actorSystemBuilder(
            PekkoProperties properties,
            RootGuardianSupplierWrapper rootGuardianSupplierWrapper) {
        return new DefaultSpringActorSystemBuilder()
                .withName("spring-boot-actor-system")
                .withConfig(properties.getConfig())
                .withRootGuardianSupplier(rootGuardianSupplierWrapper.getSupplier());
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
        Map<String, Object> actorBeans = context.getBeansWithAnnotation(SpringActor.class);

        for (Object bean : actorBeans.values()) {
            Class<?> actorClass = bean.getClass();
            SpringActor annotation = AnnotationUtils.findAnnotation(actorClass, SpringActor.class);

            // Determine the command class
            Class<?> commandClass = (annotation != null && annotation.commandClass() != Void.class)
                                    ? annotation.commandClass()
                                    : findCommandClassByConvention(actorClass);

            // Find static factory method with (String id) parameter
            Method factoryMethod = findCreateMethod(actorClass);
            if (factoryMethod == null) {
                throw new IllegalStateException("No valid static create(String) method found in " + actorClass.getName());
            }

            registry.register(commandClass, id -> {
                try {
                    return (Behavior<?>) factoryMethod.invoke(null, id);
                } catch (Exception e) {
                    throw new RuntimeException("Failed to invoke create(id) on " + actorClass.getName(), e);
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

    private Class<?> findCommandClassByConvention(Class<?> actorClass) {
        for (Class<?> nested : actorClass.getDeclaredClasses()) {
            if (nested.getSimpleName().equals("Command")) {
                return nested;
            }
        }
        throw new IllegalStateException("Cannot infer command class for actor: " + actorClass.getName());
    }
}

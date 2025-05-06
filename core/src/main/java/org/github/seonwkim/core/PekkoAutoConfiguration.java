package org.github.seonwkim.core;

import java.util.function.Supplier;

import org.apache.pekko.actor.typed.Behavior;
import org.github.seonwkim.core.impl.DefaultActorSystemBuilder;
import org.github.seonwkim.core.impl.DefaultActorSystemInstance;
import org.github.seonwkim.core.impl.DefaultRootGuardian;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(PekkoProperties.class)
@ConditionalOnProperty(prefix = "actor.pekko", name = "enabled", havingValue = "true")
@ComponentScan(basePackages = "org.github.seonwkim.core")
public class PekkoAutoConfiguration {

    @Bean
    public ActorSystemBuilder actorSystemBuilder(
            PekkoProperties properties,
            Supplier<Behavior<RootGuardian.Command>> rootGuardianSupplier) {
        return new DefaultActorSystemBuilder()
                .withName("spring-boot-actor-system")
                .withConfig(properties.getConfig())
                .withRootGuardianSupplier(rootGuardianSupplier);
    }

    @Bean
    public ActorSystemInstance actorSystem(ActorSystemBuilder builder) {
        return new DefaultActorSystemInstance(builder.build());
    }

    @Bean
    public Supplier<Behavior<RootGuardian.Command>> rootGuardianSupplier(ActorTypeRegistry actorTypeRegistry) {
        return () -> RootGuardian.create(actorTypeRegistry);
    }

    @Bean
    public ActorTypeRegistry actorTypeRegistry() {
        // TODO
        return new ActorTypeRegistry();
    }
}

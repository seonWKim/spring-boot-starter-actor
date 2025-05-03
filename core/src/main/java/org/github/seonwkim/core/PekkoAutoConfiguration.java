package org.github.seonwkim.core;

import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.github.seonwkim.core.impl.DefaultActorSystemBuilder;
import org.github.seonwkim.core.impl.DefaultActorSystemInstance;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(PekkoProperties.class)
@ConditionalOnProperty(prefix = "actor.pekko", name = "enabled", havingValue = "true")
public class PekkoAutoConfiguration {

    @Bean
    public ActorSystemBuilder actorSystemBuilder(PekkoProperties properties) {
        return new DefaultActorSystemBuilder()
                .withConfig(properties.getConfig())
                .withRootBehavior(() -> Behaviors.setup(ctx -> Behaviors.empty()));
    }

    @Bean
    public ActorSystemInstance actorSystem(ActorSystemBuilder builder) {
        return new DefaultActorSystemInstance(builder.build());
    }
}

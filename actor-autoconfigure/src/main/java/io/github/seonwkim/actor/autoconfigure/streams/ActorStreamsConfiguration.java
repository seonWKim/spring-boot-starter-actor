package io.github.seonwkim.actor.autoconfigure.streams;

import io.github.seonwkim.actor.SpringActorSystem;
import io.github.seonwkim.actor.streams.ActorStreams;
import io.github.seonwkim.actor.streams.internal.PekkoActorStreams;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** The {@link ActorStreams} bean, when {@code actor-streams} is on the application's classpath. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(name = "io.github.seonwkim.actor.streams.internal.PekkoActorStreams")
public class ActorStreamsConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public ActorStreams actorStreams(SpringActorSystem actorSystem) {
        return new PekkoActorStreams(actorSystem);
    }
}

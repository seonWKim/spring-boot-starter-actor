package io.github.seonwkim.actor.autoconfigure.streams;

import io.github.seonwkim.actor.SpringActorSystem;
import io.github.seonwkim.actor.autoconfigure.ActorConfiguration;
import io.github.seonwkim.actor.autoconfigure.ConditionalOnActorEnabled;
import io.github.seonwkim.actor.streams.ActorStreams;
import io.github.seonwkim.actor.streams.internal.PekkoActorStreams;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/** The {@link ActorStreams} bean, when {@code actor-streams} is on the application's classpath. */
@AutoConfiguration(after = ActorConfiguration.class)
@ConditionalOnActorEnabled
@ConditionalOnClass(name = "io.github.seonwkim.actor.streams.internal.PekkoActorStreams")
public class ActorStreamsConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public ActorStreams actorStreams(SpringActorSystem actorSystem) {
        return new PekkoActorStreams(actorSystem);
    }
}

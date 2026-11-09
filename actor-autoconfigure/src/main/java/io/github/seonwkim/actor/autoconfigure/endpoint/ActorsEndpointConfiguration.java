package io.github.seonwkim.actor.autoconfigure.endpoint;

import io.github.seonwkim.actor.SpringActorSystem;
import io.github.seonwkim.actor.internal.shard.ShardedActorRegistry;
import io.github.seonwkim.actor.internal.shard.ShardingStats;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The {@code actors} Actuator endpoint, in cluster mode when Actuator is on the classpath. Spring
 * Boot exposes it only when {@code management.endpoints.web.exposure.include} names it, and
 * {@code management.endpoint.actors.access=none} turns it off.
 *
 * <p>The conditions need no Actuator class to evaluate, so Actuator may be absent.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(
        name = {
            "org.springframework.boot.actuate.endpoint.annotation.Endpoint",
            "io.github.seonwkim.actor.internal.shard.ShardingStats"
        })
@ConditionalOnProperty(name = "spring.actor.provider", havingValue = "cluster")
public class ActorsEndpointConfiguration {

    @Bean
    public ActorsEndpoint actorsEndpoint(SpringActorSystem actorSystem, ShardedActorRegistry registry) {
        return new ActorsEndpoint(actorSystem, new ShardingStats(actorSystem, registry));
    }
}

package io.github.seonwkim.actor.autoconfigure.metrics;

import io.github.seonwkim.actor.internal.ActorInstrumentation;
import io.github.seonwkim.actor.metrics.agent.MetricsAgent;
import io.github.seonwkim.actor.metrics.internal.hooks.ActorMeters;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Actor meters ({@code actor-metrics}) without the Java agent, when Micrometer is on the classpath:
 * the actor system the starter builds applies the {@link ActorMeters} hooks. With no
 * {@code MeterRegistry} bean the hooks do nothing. {@code spring.actor.metrics.enabled=false}
 * turns them off.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(
        name = {
            "io.micrometer.core.instrument.MeterRegistry",
            "io.github.seonwkim.actor.metrics.internal.hooks.ActorMeters"
        })
@ConditionalOnProperty(name = "spring.actor.metrics.enabled", matchIfMissing = true)
public class ActorMetricsConfiguration {

    @Bean
    public ActorInstrumentation actorMeters(
            ObjectProvider<MeterRegistry> meterRegistry,
            @Value("${spring.actor.metrics.path-tag-limit:0}") int pathTagLimit) {
        MeterRegistry registry = meterRegistry.getIfAvailable();
        if (registry == null) {
            return new ActorInstrumentation() {};
        }
        return new ActorMeters(registry, MetricsAgent.isAgentLoaded(), pathTagLimit);
    }
}

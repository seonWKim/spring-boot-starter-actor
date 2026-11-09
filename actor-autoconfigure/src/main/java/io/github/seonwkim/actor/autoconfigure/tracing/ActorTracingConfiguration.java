package io.github.seonwkim.actor.autoconfigure.tracing;

import io.github.seonwkim.actor.internal.MessageTracing;
import io.github.seonwkim.actor.internal.ObservationMessageTracing;
import io.micrometer.observation.ObservationRegistry;
import javax.annotation.Nullable;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Carries the current trace across {@code tell} and {@code ask} through Micrometer Observation, when
 * the application has an {@link ObservationRegistry} bean (Spring Boot Actuator creates one). Without
 * one, messages are sent as they are.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(name = "io.micrometer.observation.ObservationRegistry")
public class ActorTracingConfiguration {

    /**
     * Null, so the actor system has no tracing, when there is no {@link ObservationRegistry}. This
     * configuration is imported by {@code @EnableActorSupport}, before Spring Boot's auto-configuration
     * registers the registry, so {@code @ConditionalOnBean} could not see it.
     */
    @Bean
    @Nullable public MessageTracing actorMessageTracing(ObjectProvider<ObservationRegistry> registry) {
        ObservationRegistry found = registry.getIfAvailable();
        return found == null || found.isNoop() ? null : new ObservationMessageTracing(found);
    }
}

package io.github.seonwkim.actor.autoconfigure.tracing;

import io.github.seonwkim.actor.autoconfigure.ActorConfiguration;
import io.github.seonwkim.actor.autoconfigure.ConditionalOnActorEnabled;
import io.github.seonwkim.actor.internal.MessageTracing;
import io.github.seonwkim.actor.internal.ObservationMessageTracing;
import io.micrometer.observation.ObservationRegistry;
import javax.annotation.Nullable;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;

/**
 * Carries the current trace across {@code tell} and {@code ask} through Micrometer Observation, when
 * the application has an {@link ObservationRegistry} bean (Spring Boot Actuator creates one). Without
 * one, messages are sent as they are.
 */
@AutoConfiguration(after = ActorConfiguration.class)
@ConditionalOnActorEnabled
@ConditionalOnClass(name = "io.micrometer.observation.ObservationRegistry")
public class ActorTracingConfiguration {

    /**
     * Null, so the actor system has no tracing, when there is no {@link ObservationRegistry}. Read
     * through an {@link ObjectProvider} rather than {@code @ConditionalOnBean}, so the result does not
     * depend on the order the registry's own auto-configuration runs in.
     */
    @Bean
    @Nullable public MessageTracing actorMessageTracing(ObjectProvider<ObservationRegistry> registry) {
        ObservationRegistry found = registry.getIfAvailable();
        return found == null || found.isNoop() ? null : new ObservationMessageTracing(found);
    }
}

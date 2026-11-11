package io.github.seonwkim.actor.autoconfigure.generation;

import io.github.seonwkim.actor.SpringActorSystem;
import io.github.seonwkim.actor.autoconfigure.ActorConfiguration;
import io.github.seonwkim.actor.autoconfigure.ActorProperties;
import io.github.seonwkim.actor.autoconfigure.ConditionalOnActorEnabled;
import io.github.seonwkim.actor.autoconfigure.cluster.StoreConfiguration;
import io.github.seonwkim.actor.generation.ActivationEvent;
import io.github.seonwkim.actor.generation.DeploymentGeneration;
import io.github.seonwkim.actor.spi.GenerationStore;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.util.Objects;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Blue-green activation and fencing ({@link DeploymentGeneration}) when {@code spring.actor.generation.color}
 * is set, on the {@link GenerationStore} that {@code spring.actor.generation.store} picks or the
 * application defines. With Actuator on the classpath, also the {@code generation} endpoint, and with
 * Micrometer the {@code actor.generation.activations} meter.
 *
 * <p>Each nested class carries all of its own conditions, and none needs an Actuator or Micrometer class
 * to evaluate.
 */
@AutoConfiguration(after = {ActorConfiguration.class, StoreConfiguration.class})
@ConditionalOnActorEnabled
@ConditionalOnProperty(name = GenerationConfiguration.COLOR)
public class GenerationConfiguration {

    static final String COLOR = "spring.actor.generation.color";
    static final String METER = "actor.generation.activations";

    @Bean
    public StoreDeploymentGeneration deploymentGeneration(
            ObjectProvider<GenerationStore> store,
            ActorProperties properties,
            SpringActorSystem actorSystem,
            ApplicationEventPublisher events) {
        GenerationStore generationStore = store.getIfUnique(() -> {
            throw new IllegalStateException(
                    COLOR + " needs one GenerationStore bean: set spring.actor.generation.store"
                            + " to jdbc (with actor-jdbc) or redis (with actor-redis), or define your own GenerationStore bean");
        });
        ActorProperties.Generation generation = properties.getGeneration();
        String name = generation.getName();
        return new StoreDeploymentGeneration(
                generationStore,
                name != null ? name : actorSystem.getName(),
                Objects.requireNonNull(generation.getColor()),
                generation.getRefresh(),
                Clock.systemUTC(),
                events);
    }

    /** Counts each attempt by outcome. */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "io.micrometer.core.instrument.MeterRegistry")
    @ConditionalOnProperty(name = COLOR)
    static class Meter {

        @Bean
        ApplicationListener<ActivationEvent> generationActivationMeter(ObjectProvider<MeterRegistry> registries) {
            return event -> registries.forEach(registry -> Counter.builder(METER)
                    .description("Blue-green activation attempts, by outcome")
                    .tag("outcome", event.result().outcome().name())
                    .register(registry)
                    .increment());
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "org.springframework.boot.actuate.endpoint.annotation.Endpoint")
    @ConditionalOnProperty(name = COLOR)
    static class Endpoint {

        @Bean
        GenerationEndpoint generationEndpoint(StoreDeploymentGeneration generation) {
            return new GenerationEndpoint(generation);
        }

        @Bean
        GenerationEndpointWebExtension generationEndpointWebExtension(GenerationEndpoint endpoint) {
            return new GenerationEndpointWebExtension(endpoint);
        }
    }
}

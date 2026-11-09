package io.github.seonwkim.actor.autoconfigure.tracing;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.seonwkim.actor.SpringActorSystem;
import io.github.seonwkim.actor.autoconfigure.ActorConfiguration;
import io.github.seonwkim.actor.internal.MessageTracingSetup;
import io.micrometer.observation.ObservationRegistry;
import org.apache.pekko.actor.typed.ActorSystem;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.annotation.UserConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/** Tracing is on only when the application has an ObservationRegistry bean. */
class TracingOffTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(UserConfigurations.of(ActorConfiguration.class, ActorTracingConfiguration.class))
            .withPropertyValues("spring.actor.shutdown.exit-process=false");

    @Test
    void withoutAnObservationRegistryMessagesAreNotTraced() {
        runner.run(context ->
                assertThat(tracing(context.getBean(SpringActorSystem.class))).isFalse());
    }

    @Test
    void withAnObservationRegistryMessagesAreTraced() {
        runner.withBean(ObservationRegistry.class, TracingOffTest::registryWithAHandler)
                .run(context -> assertThat(tracing(context.getBean(SpringActorSystem.class)))
                        .isTrue());
    }

    private static ObservationRegistry registryWithAHandler() {
        ObservationRegistry registry = ObservationRegistry.create();
        registry.observationConfig().observationHandler(context -> true);
        return registry;
    }

    private static boolean tracing(SpringActorSystem system) {
        return MessageTracingSetup.of(system.unwrap(ActorSystem.class)) != null;
    }
}

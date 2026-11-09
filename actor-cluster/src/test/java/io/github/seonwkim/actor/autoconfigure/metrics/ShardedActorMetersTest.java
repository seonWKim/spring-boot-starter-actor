package io.github.seonwkim.actor.autoconfigure.metrics;

import static org.awaitility.Awaitility.await;

import io.github.seonwkim.actor.EnableActorSupport;
import io.github.seonwkim.actor.SpringActorSystem;
import io.github.seonwkim.actor.fixture.TestShardedActor;
import io.github.seonwkim.actor.metrics.internal.hooks.ActorMeters;
import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

/** Sharded entities get the agent-free actor meters like any other actor. */
class ShardedActorMetersTest {

    @SpringBootApplication
    @EnableActorSupport
    static class TestApp {}

    @Test
    void messagesToShardedEntitiesAreCountedUnderTheActorClass() {
        try (ConfigurableApplicationContext context = new SpringApplicationBuilder(TestApp.class)
                .sources(TestShardedActor.class)
                .web(WebApplicationType.NONE)
                .properties(
                        "spring.actor.provider=cluster",
                        "spring.actor.remote.port=0",
                        "spring.actor.pekko.management.enabled=false",
                        "spring.actor.cluster.join-self=true")
                .run()) {
            SpringActorSystem system = context.getBean(SpringActorSystem.class);
            for (int i = 0; i < 3; i++) {
                system.sharded(TestShardedActor.class)
                        .withId("entity-" + i)
                        .get()
                        .tell(new TestShardedActor.Ping("hello"));
            }

            await().atMost(Duration.ofSeconds(20)).until(() -> {
                FunctionCounter processed = context.getBean(MeterRegistry.class)
                        .find(ActorMeters.PROCESSED)
                        .tag("actor.class", "TestShardedActor")
                        .functionCounter();
                return processed != null && processed.count() == 3;
            });
        }
    }
}

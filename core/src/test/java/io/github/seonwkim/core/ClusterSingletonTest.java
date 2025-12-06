package io.github.seonwkim.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.seonwkim.core.serialization.JsonSerializable;
import java.time.Duration;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;
import org.springframework.test.context.TestPropertySource;

/**
 * Tests for cluster singleton functionality.
 *
 * <p>Note: These tests run in local mode (not cluster mode) to verify that:
 * <ul>
 *   <li>The cluster singleton API is correctly integrated</li>
 *   <li>Attempting to spawn a cluster singleton in local mode fails gracefully</li>
 *   <li>The fluent API for cluster singletons works correctly</li>
 * </ul>
 *
 * <p>For full cluster singleton testing (multi-node failover, etc.), see the integration
 * tests in the example/cluster module.
 */
@SpringBootTest(classes = ClusterSingletonTest.TestApp.class)
@TestPropertySource(
        properties = {
            "spring.actor.pekko.loglevel=INFO",
            "spring.actor.pekko.actor.provider=local" // Local mode - cluster singleton should fail
        })
public class ClusterSingletonTest {

    @Autowired
    private ApplicationContext applicationContext;

    @SpringBootApplication(scanBasePackages = "io.github.seonwkim.core")
    public static class ClusterSingletonTestApp {}

    /**
     * Simple test actor for cluster singleton tests.
     */
    @Component
    public static class SingletonTestActor
            implements SpringActorWithContext<SingletonTestActor.Command, SpringActorContext> {

        public interface Command extends JsonSerializable {}

        public static class GetCount extends AskCommand<CountResponse> implements Command {
            public GetCount() {}
        }

        public static class Increment implements Command {}

        public static class CountResponse implements JsonSerializable {
            public final int count;

            public CountResponse(int count) {
                this.count = count;
            }
        }

        private final AtomicInteger count = new AtomicInteger(0);

        @Override
        public SpringActorBehavior<Command> create(SpringActorContext actorContext) {
            return SpringActorBehavior.builder(Command.class, actorContext)
                    .onMessage(Increment.class, (ctx, msg) -> {
                        count.incrementAndGet();
                        ctx.getLog().debug("Count incremented to {}", count.get());
                        return Behaviors.same();
                    })
                    .onMessage(GetCount.class, (ctx, msg) -> {
                        msg.reply(new CountResponse(count.get()));
                        return Behaviors.same();
                    })
                    .build();
        }
    }

    @Nested
    @SpringBootTest(classes = TestApp.class)
    @TestPropertySource(properties = {"spring.actor.pekko.loglevel=INFO", "spring.actor.pekko.actor.provider=local"})
    class LocalModeTests {

        @Autowired
        private SpringActorSystem actorSystem;

        /**
         * Tests that attempting to spawn a cluster singleton in local mode fails gracefully.
         */
        @Test
        public void testClusterSingletonInLocalModeFails() {
            // When: Attempting to spawn a cluster singleton in local mode
            CompletionStage<SpringActorHandle<SingletonTestActor.Command>> result = actorSystem
                    .actor(SingletonTestActor.class)
                    .withId("singleton-test")
                    .asClusterSingleton()
                    .spawn();

            // Then: Should fail with IllegalStateException
            assertThatThrownBy(() -> result.toCompletableFuture().join())
                    .hasCauseInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Cluster singleton requested but cluster mode is not enabled");
        }

        /**
         * Tests that regular (non-singleton) actors still work in local mode.
         */
        @Test
        public void testRegularActorWorksInLocalMode() throws Exception {
            // Given: A regular (non-singleton) actor
            SpringActorHandle<SingletonTestActor.Command> actor = actorSystem
                    .actor(SingletonTestActor.class)
                    .withId("regular-actor")
                    // Note: NOT calling asClusterSingleton()
                    .spawn()
                    .toCompletableFuture()
                    .get();

            // When: Sending messages to the actor
            actor.tell(new SingletonTestActor.Increment());
            actor.tell(new SingletonTestActor.Increment());
            actor.tell(new SingletonTestActor.Increment());

            SingletonTestActor.CountResponse response = actor.ask(new SingletonTestActor.GetCount())
                    .withTimeout(Duration.ofSeconds(3))
                    .execute()
                    .toCompletableFuture()
                    .get();

            // Then: The actor should work normally
            assertThat(response.count).isEqualTo(3);
        }
    }

    /**
     * Test configuration.
     */
    @Configuration
    @EnableAutoConfiguration
    @ComponentScan
    public static class TestApp {}
}

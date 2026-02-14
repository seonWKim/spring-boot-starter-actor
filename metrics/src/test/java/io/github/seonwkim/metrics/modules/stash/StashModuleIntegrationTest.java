package io.github.seonwkim.metrics.modules.stash;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

import io.github.seonwkim.metrics.agent.MetricsAgent;
import io.github.seonwkim.metrics.core.MetricsConfiguration;
import io.github.seonwkim.metrics.core.MetricsRegistry;
import io.github.seonwkim.metrics.testing.TestMetricsBackend;
import java.util.concurrent.TimeUnit;
import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import scala.concurrent.Await;
import scala.concurrent.duration.Duration;

/**
 * Integration test for StashModule with Pekko Typed actors using Behaviors.withStash.
 */
class StashModuleIntegrationTest {

    private TestMetricsBackend metricsBackend;
    private MetricsRegistry metricsRegistry;
    private StashModule module;
    private ActorSystem<Object> actorSystem;

    @BeforeEach
    void setUp() {
        metricsBackend = new TestMetricsBackend();
        MetricsConfiguration.FilterConfig filters = MetricsConfiguration.FilterConfig.builder()
                .includeActors("**/user/**")
                .excludeActors("**/system/**")
                .build();
        MetricsConfiguration config = MetricsConfiguration.builder()
                .enabled(true)
                .tag("test", "stash-integration")
                .filters(filters)
                .build();

        metricsRegistry = MetricsRegistry.builder()
                .configuration(config)
                .backend(metricsBackend)
                .build();

        module = new StashModule();
        metricsRegistry.registerModule(module);
        MetricsAgent.setRegistry(metricsRegistry);

        actorSystem = ActorSystem.create(stashingActorBehavior(), "stash-test-system");
    }

    @AfterEach
    void tearDown() throws Exception {
        if (actorSystem != null) {
            actorSystem.terminate();
            Await.result(actorSystem.whenTerminated(), Duration.apply(5, TimeUnit.SECONDS));
        }
        if (module != null) {
            module.shutdown();
        }
        MetricsAgent.setRegistry(null);
    }

    @Test
    void testStashSizeIncrementsWhenActorStashes() throws Exception {
        Thread.sleep(200);

        // Send messages that will be stashed (actor starts in "waiting" state)
        actorSystem.tell("stash1");
        actorSystem.tell("stash2");
        actorSystem.tell("stash3");

        await().atMost(3, TimeUnit.SECONDS)
                .pollInterval(50, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> {
                    double stashSize = metricsBackend.getGaugeValue("actor.stash.size");
                    assertTrue(
                            stashSize >= 1,
                            String.format("Stash size should be >= 1 when messages stashed, got: %.0f", stashSize));
                });
    }

    @Test
    void testStashSizeDecrementsOnUnstashAll() throws Exception {
        Thread.sleep(200);

        actorSystem.tell("stash1");
        actorSystem.tell("stash2");
        Thread.sleep(300);

        // Send "ready" to trigger unstashAll
        actorSystem.tell("ready");

        await().atMost(3, TimeUnit.SECONDS)
                .pollInterval(50, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> {
                    double stashSize = metricsBackend.getGaugeValue("actor.stash.size");
                    assertTrue(
                            stashSize <= 1,
                            String.format("Stash size should be 0 or 1 after unstashAll, got: %.0f", stashSize));
                });
    }

    private static Behavior<Object> stashingActorBehavior() {
        return Behaviors.setup(ctx -> Behaviors.withStash(10, stash -> Behaviors.receive(Object.class)
                .onMessage(String.class, msg -> {
                    if ("ready".equals(msg)) {
                        return stash.unstashAll(Behaviors.receive(Object.class)
                                .onMessage(String.class, m -> {
                                    // Process stashed messages
                                    return Behaviors.same();
                                })
                                .build());
                    }
                    // Stash until "ready" is received
                    stash.stash(msg);
                    return Behaviors.same();
                })
                .build()));
    }
}

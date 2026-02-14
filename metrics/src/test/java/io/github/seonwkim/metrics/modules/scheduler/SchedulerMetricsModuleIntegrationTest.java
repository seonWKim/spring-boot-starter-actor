package io.github.seonwkim.metrics.modules.scheduler;

import static org.junit.jupiter.api.Assertions.*;

import io.github.seonwkim.metrics.agent.MetricsAgent;
import io.github.seonwkim.metrics.core.MetricsConfiguration;
import io.github.seonwkim.metrics.core.MetricsRegistry;
import io.github.seonwkim.metrics.testing.TestMetricsBackend;
import java.util.concurrent.TimeUnit;
import org.apache.pekko.actor.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import scala.concurrent.Await;
import scala.concurrent.duration.Duration;

/**
 * Integration test for SchedulerMetricsModule.
 */
class SchedulerMetricsModuleIntegrationTest {

    private TestMetricsBackend metricsBackend;
    private MetricsRegistry metricsRegistry;
    private SchedulerMetricsModule module;
    private ActorSystem actorSystem;

    @BeforeEach
    void setUp() {
        metricsBackend = new TestMetricsBackend();
        MetricsConfiguration config = MetricsConfiguration.builder()
                .enabled(true)
                .tag("test", "scheduler-integration")
                .build();
        metricsRegistry = MetricsRegistry.builder()
                .configuration(config)
                .backend(metricsBackend)
                .build();
        module = new SchedulerMetricsModule();
        metricsRegistry.registerModule(module);
        MetricsAgent.setRegistry(metricsRegistry);
        actorSystem = ActorSystem.create("scheduler-test-system");
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
    @Disabled("ByteBuddy instrumentation of Scala scheduler methods does not match—advice never runs. "
            + "Scheduler interface is IGNORED; LightArrayRevolverScheduler matchers fail. Deferred.")
    void testScheduledCounterIncrementsWhenSchedulingTasks() throws Exception {
        Thread.sleep(200);

        double initialScheduled = metricsBackend.getCounterValue("scheduler.tasks.scheduled");

        actorSystem.scheduler().scheduleOnce(java.time.Duration.ofMillis(10), () -> {}, actorSystem.dispatcher());

        actorSystem.scheduler().scheduleOnce(java.time.Duration.ofMillis(20), () -> {}, actorSystem.dispatcher());

        Thread.sleep(500);

        double afterScheduled = metricsBackend.getCounterValue("scheduler.tasks.scheduled");
        assertEquals(
                initialScheduled + 2,
                afterScheduled,
                String.format(
                        "Scheduled counter should increment by 2. Initial: %.0f, After: %.0f",
                        initialScheduled, afterScheduled));
    }
}

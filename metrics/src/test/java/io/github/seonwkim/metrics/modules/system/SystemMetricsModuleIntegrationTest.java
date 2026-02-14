package io.github.seonwkim.metrics.modules.system;

import static org.junit.jupiter.api.Assertions.*;

import io.github.seonwkim.metrics.agent.MetricsAgent;
import io.github.seonwkim.metrics.core.MetricsConfiguration;
import io.github.seonwkim.metrics.core.MetricsRegistry;
import io.github.seonwkim.metrics.testing.TestMetricsBackend;
import java.util.concurrent.TimeUnit;
import org.apache.pekko.actor.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import scala.concurrent.Await;
import scala.concurrent.duration.Duration;

/**
 * Integration test for SystemMetricsModule that verifies ByteBuddy instrumentation
 * works with real Pekko actors and dead letter handling.
 */
class SystemMetricsModuleIntegrationTest {

    private TestMetricsBackend metricsBackend;
    private MetricsRegistry metricsRegistry;
    private SystemMetricsModule module;
    private ActorSystem actorSystem;

    @BeforeEach
    void setUp() {
        metricsBackend = new TestMetricsBackend();

        MetricsConfiguration config = MetricsConfiguration.builder()
                .enabled(true)
                .tag("test", "system-metrics-integration")
                .build();

        metricsRegistry = MetricsRegistry.builder()
                .configuration(config)
                .backend(metricsBackend)
                .build();

        module = new SystemMetricsModule();
        metricsRegistry.registerModule(module);

        MetricsAgent.setRegistry(metricsRegistry);

        actorSystem = ActorSystem.create("dead-letter-test-system");
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
    void testUnhandledMessageCounterIncrementsWhenActorDoesNotHandleMessageType() throws Exception {
        Thread.sleep(200);

        double initialUnhandled = metricsBackend.getCounterValue("system.unhandled-messages");

        // Actor that only handles String, not Integer
        ActorRef actor = actorSystem.actorOf(Props.create(StringOnlyActor.class), "string-only-actor");
        actor.tell(42, ActorRef.noSender()); // Integer - not handled

        Thread.sleep(500);

        double afterUnhandled = metricsBackend.getCounterValue("system.unhandled-messages");
        assertEquals(
                initialUnhandled + 1,
                afterUnhandled,
                String.format(
                        "Unhandled message counter should increment when actor doesn't handle message type. Initial: %.0f, After: %.0f",
                        initialUnhandled, afterUnhandled));
    }

    @Test
    void testDeadLetterCounterIncrementsWhenSendingToTerminatedActor() throws Exception {
        Thread.sleep(200);

        double initialDeadLetters = metricsBackend.getCounterValue("system.dead-letters");

        // Create actor, stop it, then send message -> dead letter
        ActorRef actor = actorSystem.actorOf(Props.create(SimpleActor.class), "soon-to-be-stopped");
        actorSystem.stop(actor);

        Thread.sleep(500); // Wait for termination

        // Send to terminated actor -> results in dead letter
        actor.tell("hello", ActorRef.noSender());

        Thread.sleep(500);

        double afterDeadLetter = metricsBackend.getCounterValue("system.dead-letters");
        assertEquals(
                initialDeadLetters + 1,
                afterDeadLetter,
                String.format(
                        "Dead letter counter should increment when sending to terminated actor. Initial: %.0f, After: %.0f",
                        initialDeadLetters, afterDeadLetter));
    }

    /** Actor that only handles String - other types trigger UnhandledMessage. */
    public static class StringOnlyActor extends AbstractActor {
        @Override
        public Receive createReceive() {
            return receiveBuilder().match(String.class, msg -> {}).build();
        }
    }

    public static class SimpleActor extends AbstractActor {
        @Override
        public Receive createReceive() {
            return receiveBuilder().matchAny(msg -> {}).build();
        }
    }
}

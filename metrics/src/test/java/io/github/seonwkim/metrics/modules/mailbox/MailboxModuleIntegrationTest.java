package io.github.seonwkim.metrics.modules.mailbox;

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
 * Integration test for MailboxModule that verifies ByteBuddy instrumentation
 * works with real Pekko Classic actors.
 */
class MailboxModuleIntegrationTest {

    private TestMetricsBackend metricsBackend;
    private MetricsRegistry metricsRegistry;
    private MailboxModule module;
    private ActorSystem actorSystem;

    @BeforeEach
    void setUp() {
        // Create test metrics backend
        metricsBackend = new TestMetricsBackend();

        // Create configuration - include user actors, exclude system actors
        MetricsConfiguration.FilterConfig filters = MetricsConfiguration.FilterConfig.builder()
                .includeActors("**/user/**")
                .excludeActors("**/system/**")
                .build();

        MetricsConfiguration config = MetricsConfiguration.builder()
                .enabled(true)
                .tag("test", "mailbox-integration")
                .filters(filters)
                .build();

        // Create registry
        metricsRegistry = MetricsRegistry.builder()
                .configuration(config)
                .backend(metricsBackend)
                .build();

        // Create and register module
        module = new MailboxModule();
        metricsRegistry.registerModule(module);

        // Set the registry in the agent so the advice classes can access it
        MetricsAgent.setRegistry(metricsRegistry);

        // Create actor system (Classic API)
        actorSystem = ActorSystem.create("test-system");
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
    void testMailboxSizeIncrementsOnEnqueue() throws Exception {
        // Wait for system to stabilize
        Thread.sleep(200);

        // Create actor
        ActorRef actor = actorSystem.actorOf(Props.create(SlowActor.class), "slow-actor");

        // Send messages (actor is slow, so they queue up)
        actor.tell(new TestMessage("msg1"), ActorRef.noSender());
        actor.tell(new TestMessage("msg2"), ActorRef.noSender());
        actor.tell(new TestMessage("msg3"), ActorRef.noSender());

        // Wait a bit for enqueuing
        Thread.sleep(200);

        // Mailbox size should be > 0 (messages are queued)
        double mailboxSize = metricsBackend.getGaugeValue("actor.mailbox.size");
        assertTrue(mailboxSize > 0, String.format("Mailbox size should be greater than 0, got: %.0f", mailboxSize));
    }

    @Test
    void testMailboxSizeDecrementsOnProcessing() throws Exception {
        // Wait for system to stabilize
        Thread.sleep(200);

        // Create fast actor that processes immediately
        ActorRef actor = actorSystem.actorOf(Props.create(FastActor.class), "fast-actor");

        // Send messages
        actor.tell(new TestMessage("msg1"), ActorRef.noSender());
        actor.tell(new TestMessage("msg2"), ActorRef.noSender());

        // Wait for processing to complete
        Thread.sleep(500);

        // Mailbox should be empty (or close to it)
        double mailboxSize = metricsBackend.getGaugeValue("actor.mailbox.size");
        assertTrue(
                mailboxSize <= 1,
                String.format("Mailbox size should be 0 or 1 after processing, got: %.0f", mailboxSize));
    }

    @Test
    void testMailboxTimeIsRecorded() throws Exception {
        // Wait for system to stabilize
        Thread.sleep(200);

        // Create actor and send message
        ActorRef actor = actorSystem.actorOf(Props.create(FastActor.class), "timer-actor");
        actor.tell(new TestMessage("test"), ActorRef.noSender());

        Thread.sleep(500);

        long timerCount = metricsBackend.getTimerCount("actor.mailbox.time");
        assertTrue(timerCount > 0, "Mailbox time should be recorded");

        long totalTime = metricsBackend.getTimerTotalTime("actor.mailbox.time");
        assertTrue(totalTime > 0, "Total mailbox time should be greater than 0");
    }

    @Test
    void testMultipleActorsHaveSeparateMailboxes() throws Exception {
        // Wait for system to stabilize
        Thread.sleep(200);

        // Create actors of different classes (to get separate gauges per class)
        ActorRef actor1 = actorSystem.actorOf(Props.create(SlowActor.class), "actor-1");
        ActorRef actor2 = actorSystem.actorOf(Props.create(FastActor.class), "actor-2");

        // Send messages to each
        actor1.tell(new TestMessage("msg1"), ActorRef.noSender());
        actor1.tell(new TestMessage("msg2"), ActorRef.noSender());
        actor2.tell(new TestMessage("msg3"), ActorRef.noSender());

        Thread.sleep(200);

        // Should have gauge registered for 2 different actor classes
        int gaugeCount = metricsBackend.gaugeCount();
        assertTrue(gaugeCount >= 2, String.format("Should have at least 2 mailbox gauges (one per actor class), got: %d", gaugeCount));
    }

    @Test
    void testMailboxMetricsHaveActorClassTag() throws Exception {
        // Wait for system to stabilize
        Thread.sleep(200);

        // Create actor and send message
        ActorRef actor = actorSystem.actorOf(Props.create(FastActor.class), "tagged-actor");
        actor.tell(new TestMessage("test"), ActorRef.noSender());

        Thread.sleep(500);

        assertTrue(
                metricsBackend.hasMetricWithTag("actor.mailbox.size", "actor.class"),
                "Mailbox size gauge should have actor.class tag");
        assertTrue(
                metricsBackend.hasMetricWithTag("actor.mailbox.time", "actor.class"),
                "Mailbox timer should have actor.class tag");
    }

    @Test
    void testMailboxTimeHasMessageTypeTag() throws Exception {
        // Wait for system to stabilize
        Thread.sleep(200);

        // Create actor and send message
        ActorRef actor = actorSystem.actorOf(Props.create(FastActor.class), "message-type-actor");
        actor.tell(new TestMessage("test"), ActorRef.noSender());

        Thread.sleep(500);

        assertTrue(
                metricsBackend.hasMetricWithTag("actor.mailbox.time", "message.type"),
                "Mailbox timer should have message.type tag");
    }

    // Test actors

    public static class SlowActor extends AbstractActor {
        @Override
        public Receive createReceive() {
            return receiveBuilder()
                    .match(TestMessage.class, msg -> {
                        // Simulate slow processing
                        Thread.sleep(500);
                    })
                    .build();
        }
    }

    public static class FastActor extends AbstractActor {
        @Override
        public Receive createReceive() {
            return receiveBuilder()
                    .match(TestMessage.class, msg -> {
                        // Fast processing
                    })
                    .build();
        }
    }

    // Test message
    public static class TestMessage {
        private final String value;

        public TestMessage(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }
}

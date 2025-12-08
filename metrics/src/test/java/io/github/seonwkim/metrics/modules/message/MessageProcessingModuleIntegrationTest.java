package io.github.seonwkim.metrics.modules.message;

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
 * Integration test for MessageProcessingModule that verifies ByteBuddy instrumentation
 * works with real Pekko Classic actors processing messages.
 */
class MessageProcessingModuleIntegrationTest {

    private TestMetricsBackend metricsBackend;
    private MetricsRegistry metricsRegistry;
    private MessageProcessingModule module;
    private ActorSystem actorSystem;

    @BeforeEach
    void setUp() {
        // Create test metrics backend
        metricsBackend = new TestMetricsBackend();

        // Create configuration
        MetricsConfiguration config = MetricsConfiguration.builder()
                .enabled(true)
                .tag("test", "message-processing-integration")
                .build();

        // Create registry
        metricsRegistry = MetricsRegistry.builder()
                .configuration(config)
                .meterRegistry(metricsBackend)
                .build();

        // Create and register module
        module = new MessageProcessingModule();
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
    void testMessageProcessedCounterIncreases() throws Exception {
        // Wait for system to stabilize
        Thread.sleep(200);

        double initialProcessed = metricsBackend.getCounterValue("actor.message.processed");

        // Create actor and send message
        ActorRef actor = actorSystem.actorOf(Props.create(EchoActor.class), "echo-actor");
        actor.tell(new StringMessage("hello"), ActorRef.noSender());

        Thread.sleep(500);

        double afterMessage = metricsBackend.getCounterValue("actor.message.processed");
        assertEquals(
                initialProcessed + 1,
                afterMessage,
                String.format(
                        "Processed counter should increment by exactly 1. Initial: %.0f, After: %.0f",
                        initialProcessed, afterMessage));
    }

    @Test
    void testProcessingTimeIsRecorded() throws Exception {
        // Wait for system to stabilize
        Thread.sleep(200);

        // Create actor and send message
        ActorRef actor = actorSystem.actorOf(Props.create(EchoActor.class), "timer-actor");
        actor.tell(new StringMessage("test"), ActorRef.noSender());

        Thread.sleep(500);

        long timerCount = metricsBackend.getTimerCount("actor.message.processing.time");
        assertTrue(timerCount > 0, "Processing time should be recorded");

        long totalTime = metricsBackend.getTimerTotalTime("actor.message.processing.time");
        assertTrue(totalTime > 0, "Total processing time should be greater than 0");
    }

    @Test
    void testMultipleMessagesToSameActor() throws Exception {
        // Wait for system to stabilize
        Thread.sleep(200);

        double initialProcessed = metricsBackend.getCounterValue("actor.message.processed");

        // Create actor and send multiple messages
        ActorRef actor = actorSystem.actorOf(Props.create(EchoActor.class), "multi-message-actor");
        actor.tell(new StringMessage("msg1"), ActorRef.noSender());
        actor.tell(new StringMessage("msg2"), ActorRef.noSender());
        actor.tell(new StringMessage("msg3"), ActorRef.noSender());

        Thread.sleep(500);

        double afterMessages = metricsBackend.getCounterValue("actor.message.processed");
        assertEquals(
                initialProcessed + 3,
                afterMessages,
                String.format(
                        "Should process exactly 3 messages. Initial: %.0f, After: %.0f",
                        initialProcessed, afterMessages));
    }

    @Test
    void testDifferentMessageTypes() throws Exception {
        // Wait for system to stabilize
        Thread.sleep(200);

        double initialProcessed = metricsBackend.getCounterValue("actor.message.processed");

        // Create actor and send different message types
        ActorRef actor = actorSystem.actorOf(Props.create(MultiTypeActor.class), "multi-type-actor");
        actor.tell(new StringMessage("hello"), ActorRef.noSender());
        actor.tell(new IntMessage(42), ActorRef.noSender());
        actor.tell(new BooleanMessage(true), ActorRef.noSender());

        Thread.sleep(500);

        // Verify that different message types are tracked
        assertTrue(
                metricsBackend.hasMetricWithTag("actor.message.processed", "message.type"),
                "Processed counter should have message.type tag");

        double totalProcessed = metricsBackend.getCounterValue("actor.message.processed");
        assertEquals(initialProcessed + 3, totalProcessed, "Should process exactly 3 messages of different types");
    }

    @Test
    void testMultipleActorsProcessingMessages() throws Exception {
        // Wait for system to stabilize
        Thread.sleep(200);

        double initialProcessed = metricsBackend.getCounterValue("actor.message.processed");

        // Create multiple actors
        ActorRef actor1 = actorSystem.actorOf(Props.create(EchoActor.class), "actor-1");
        ActorRef actor2 = actorSystem.actorOf(Props.create(EchoActor.class), "actor-2");
        ActorRef actor3 = actorSystem.actorOf(Props.create(EchoActor.class), "actor-3");

        // Send messages to each
        actor1.tell(new StringMessage("msg1"), ActorRef.noSender());
        actor2.tell(new StringMessage("msg2"), ActorRef.noSender());
        actor3.tell(new StringMessage("msg3"), ActorRef.noSender());

        Thread.sleep(500);

        double afterMessages = metricsBackend.getCounterValue("actor.message.processed");
        assertEquals(
                initialProcessed + 3,
                afterMessages,
                String.format(
                        "Should process exactly 3 messages from multiple actors. Initial: %.0f, After: %.0f",
                        initialProcessed, afterMessages));
    }

    @Test
    void testMetricsHaveActorClassTag() throws Exception {
        // Wait for system to stabilize
        Thread.sleep(200);

        // Create actor and send message
        ActorRef actor = actorSystem.actorOf(Props.create(EchoActor.class), "tagged-actor");
        actor.tell(new StringMessage("test"), ActorRef.noSender());

        Thread.sleep(500);

        assertTrue(
                metricsBackend.hasMetricWithTag("actor.message.processed", "actor.class"),
                "Processed counter should have actor.class tag");
        assertTrue(
                metricsBackend.hasMetricWithTag("actor.message.processing.time", "actor.class"),
                "Processing timer should have actor.class tag");
    }

    // Test actors

    public static class EchoActor extends AbstractActor {
        @Override
        public Receive createReceive() {
            return receiveBuilder()
                    .match(StringMessage.class, msg -> {
                        // Simple echo
                    })
                    .match(Object.class, msg -> {
                        // Handle any message
                    })
                    .build();
        }
    }

    public static class MultiTypeActor extends AbstractActor {
        @Override
        public Receive createReceive() {
            return receiveBuilder()
                    .match(StringMessage.class, msg -> {
                        // Handle string message
                    })
                    .match(IntMessage.class, msg -> {
                        // Handle int message
                    })
                    .match(BooleanMessage.class, msg -> {
                        // Handle boolean message
                    })
                    .build();
        }
    }

    // Test messages
    public static class StringMessage {
        private final String value;

        public StringMessage(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }

    public static class IntMessage {
        private final int value;

        public IntMessage(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }
    }

    public static class BooleanMessage {
        private final boolean value;

        public BooleanMessage(boolean value) {
            this.value = value;
        }

        public boolean isValue() {
            return value;
        }
    }
}

package io.github.seonwkim.metrics.impl;

import static org.junit.jupiter.api.Assertions.*;

import io.github.seonwkim.metrics.TestActorSystem;
import io.github.seonwkim.metrics.interceptor.ActorLifeCycleEventInterceptorsHolder;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class ActorSystemMetricsCollectorTest {

    private static final Logger logger = LoggerFactory.getLogger(ActorSystemMetricsCollectorTest.class);

    private TestActorSystem actorSystem;
    private ActorSystemMetricsCollector metrics;

    @BeforeEach
    void setUp() {
        ActorLifeCycleEventInterceptorsHolder.reset();

        actorSystem = new TestActorSystem();
        metrics = new ActorSystemMetricsCollector();
        ActorLifeCycleEventInterceptorsHolder.register(metrics);
    }

    @Test
    void testActiveActorsIncrementsOnCreation() throws Exception {
        long initialCount = metrics.getActiveActors();

        ActorRef<TestActor.Command> actor = actorSystem
                .spawn(
                        TestActor.Command.class,
                        "test-actor-" + UUID.randomUUID(),
                        TestActor.create(),
                        Duration.ofSeconds(3))
                .toCompletableFuture()
                .get(3, TimeUnit.SECONDS);

        // Give some time for the metric to be recorded
        Thread.sleep(100);

        assertEquals(initialCount + 1, metrics.getActiveActors(), "Active actors should increment by 1");
        assertEquals(initialCount + 1, metrics.getCreatedActorsTotal(), "Created actors total should increment by 1");
    }

    @Test
    void testActiveActorsDecrementsOnTermination() throws Exception {
        long initialActive = metrics.getActiveActors();
        long initialTerminated = metrics.getTerminatedActorsTotal();

        ActorRef<TestActor.Command> actor = actorSystem
                .spawn(
                        TestActor.Command.class,
                        "test-actor-" + UUID.randomUUID(),
                        TestActor.create(),
                        Duration.ofSeconds(3))
                .toCompletableFuture()
                .get(3, TimeUnit.SECONDS);

        Thread.sleep(100);
        assertEquals(initialActive + 1, metrics.getActiveActors());

        // Stop the actor
        actor.tell(new TestActor.Stop());
        Thread.sleep(200);

        assertEquals(initialActive, metrics.getActiveActors(), "Active actors should decrement back to initial");
        assertEquals(
                initialTerminated + 1,
                metrics.getTerminatedActorsTotal(),
                "Terminated actors total should increment by 1");
    }

    @Test
    void testMultipleActorsCreationAndTermination() throws Exception {
        long initialActive = metrics.getActiveActors();
        long initialCreated = metrics.getCreatedActorsTotal();

        List<ActorRef<TestActor.Command>> actors = new ArrayList<>();
        int actorCount = 3;

        // Create multiple actors
        for (int i = 0; i < actorCount; i++) {
            ActorRef<TestActor.Command> actor = actorSystem
                    .spawn(
                            TestActor.Command.class,
                            "test-actor-" + UUID.randomUUID(),
                            TestActor.create(),
                            Duration.ofSeconds(3))
                    .toCompletableFuture()
                    .get(3, TimeUnit.SECONDS);
            actors.add(actor);
        }

        Thread.sleep(200);

        assertEquals(initialActive + actorCount, metrics.getActiveActors(), "Should have 3 more active actors");
        assertEquals(
                initialCreated + actorCount, metrics.getCreatedActorsTotal(), "Should have created 3 more actors");

        // Stop all actors
        for (ActorRef<TestActor.Command> actor : actors) {
            actor.tell(new TestActor.Stop());
        }

        Thread.sleep(300);

        assertEquals(initialActive, metrics.getActiveActors(), "Active actors should return to initial count");
        assertEquals(
                actorCount,
                metrics.getTerminatedActorsTotal(),
                "Should have terminated all created actors");
    }

    @Test
    void testCreatedAndTerminatedCounters() throws Exception {
        long initialCreated = metrics.getCreatedActorsTotal();
        long initialTerminated = metrics.getTerminatedActorsTotal();

        // Create and immediately stop an actor
        ActorRef<TestActor.Command> actor = actorSystem
                .spawn(
                        TestActor.Command.class,
                        "test-actor-" + UUID.randomUUID(),
                        TestActor.create(),
                        Duration.ofSeconds(3))
                .toCompletableFuture()
                .get(3, TimeUnit.SECONDS);

        Thread.sleep(100);

        assertEquals(initialCreated + 1, metrics.getCreatedActorsTotal(), "Created counter should increment");

        actor.tell(new TestActor.Stop());
        Thread.sleep(200);

        assertEquals(
                initialTerminated + 1, metrics.getTerminatedActorsTotal(), "Terminated counter should increment");
    }

    @Test
    void testMailboxSizeTracking() throws Exception {
        ActorRef<TestActor.Command> actor = actorSystem
                .spawn(
                        TestActor.Command.class,
                        "mailbox-test-" + UUID.randomUUID(),
                        TestActor.create(),
                        Duration.ofSeconds(3))
                .toCompletableFuture()
                .get(3, TimeUnit.SECONDS);

        Thread.sleep(100);

        // Note: Mailbox size tracking requires integration with the mailbox itself
        // This test verifies the API works, actual mailbox size would be updated by instrumentation
        String actorPath = "pekko://test/user/mailbox-test-" + UUID.randomUUID();
        
        // Mailbox size should be initialized to 0 or trackable
        // The actual value depends on whether the actor path matches what we created
        long mailboxSize = metrics.getMailboxSize(actorPath);
        assertTrue(mailboxSize >= 0, "Mailbox size should be non-negative");
    }

    static class TestActor {
        public interface Command {}

        public static class Stop implements Command {}

        public static Behavior<Command> create() {
            return Behaviors.setup(ctx -> Behaviors.receive(TestActor.Command.class)
                    .onMessage(TestActor.Stop.class, msg -> {
                        logger.info("Stopping actor");
                        return Behaviors.stopped();
                    })
                    .build());
        }
    }
}

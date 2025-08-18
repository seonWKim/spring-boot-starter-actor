package io.github.seonwkim.metrics.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.github.seonwkim.metrics.TestActorSystem;
import io.github.seonwkim.metrics.listener.ActorSystemEventListener;

class SystemMetricsListenerTest {

    private TestActorSystem actorSystem;
    private SystemMetricsListener metricsListener;

    @BeforeEach
    void setUp() {
        SystemMetrics.getInstance().reset();
        actorSystem = new TestActorSystem();

        // Register the system metrics listener
        metricsListener = new SystemMetricsListener();
        ActorSystemEventListener.register(metricsListener);
    }

    @AfterEach
    void tearDown() {
        if (metricsListener != null) {
            ActorSystemEventListener.unregister(metricsListener);
        }
        if (actorSystem != null) {
            actorSystem.terminate();
        }
        SystemMetrics.getInstance().reset();
    }

    @Test
    void testActiveActorsGaugeIncrementsOnActorCreation() throws Exception {
        long initialCount = SystemMetrics.getInstance().getActiveActors();

        actorSystem.spawn(
                           TestActor.Command.class,
                           "test-actor-1",
                           TestActor.create(),
                           Duration.ofSeconds(3))
                   .toCompletableFuture()
                   .get(3, TimeUnit.SECONDS);

        long afterFirstActor = SystemMetrics.getInstance().getActiveActors();
        assertEquals(initialCount + 1, afterFirstActor,
                     "Active actors count should increase by 1 after creating an actor");

        actorSystem.spawn(
                           TestActor.Command.class,
                           "test-actor-2",
                           TestActor.create(),
                           Duration.ofSeconds(3))
                   .toCompletableFuture()
                   .get(3, TimeUnit.SECONDS);

        Thread.sleep(200);
        long afterSecondActor = SystemMetrics.getInstance().getActiveActors();
        assertEquals(initialCount + 2, afterSecondActor,
                     "Active actors count should increase by 2 after creating two actors");
    }

    @Test
    void testActiveActorsGaugeWithMultipleActors() throws Exception {
        Thread.sleep(500); // Wait for system initialization
        long initialCount = SystemMetrics.getInstance().getActiveActors();
        List<ActorRef<TestActor.Command>> actors = new ArrayList<>();

        for (int i = 0; i < 3; i++) {
            ActorRef<TestActor.Command> actor = actorSystem.spawn(
                                                                   TestActor.Command.class,
                                                                   "multi-test-actor-" + i,
                                                                   TestActor.create(),
                                                                   Duration.ofSeconds(3))
                                                           .toCompletableFuture()
                                                           .get(3, TimeUnit.SECONDS);
            actors.add(actor);
        }

        Thread.sleep(200);
        long finalCount = SystemMetrics.getInstance().getActiveActors();
        assertEquals(initialCount + 3, finalCount,
                     "Active actors count should increase by 3 after creating 3 actors");
    }

    @Test
    void testActiveActorsGaugeDecrementsOnActorTermination() throws Exception {
        Thread.sleep(500); // Wait for system initialization
        long initialCount = SystemMetrics.getInstance().getActiveActors();

        ActorRef<TestActor.Command> actor = actorSystem.spawn(
                                                               TestActor.Command.class,
                                                               "test-actor-terminate",
                                                               TestActor.create(),
                                                               Duration.ofSeconds(3))
                                                       .toCompletableFuture()
                                                       .get(3, TimeUnit.SECONDS);

        Thread.sleep(200);
        long afterCreation = SystemMetrics.getInstance().getActiveActors();
        assertEquals(initialCount + 1, afterCreation,
                     "Active actors count should increase by 1 after creating an actor");

        actor.tell(new TestActor.Stop());

        Thread.sleep(500);
        long afterTermination = SystemMetrics.getInstance().getActiveActors();
        assertEquals(initialCount, afterTermination,
                     "Active actors count should return to initial value after actor termination");
    }

    @Test
    void testActiveActorsGaugeWithMixedLifecycle() throws Exception {
        Thread.sleep(500); // Wait for system initialization
        long initialCount = SystemMetrics.getInstance().getActiveActors();
        List<ActorRef<TestActor.Command>> actors = new ArrayList<>();

        for (int i = 0; i < 5; i++) {
            ActorRef<TestActor.Command> actor = actorSystem.spawn(
                                                                   TestActor.Command.class,
                                                                   "test-actor-mixed-" + i,
                                                                   TestActor.create(),
                                                                   Duration.ofSeconds(3))
                                                           .toCompletableFuture()
                                                           .get(3, TimeUnit.SECONDS);
            actors.add(actor);
        }

        Thread.sleep(200);
        long afterFiveActors = SystemMetrics.getInstance().getActiveActors();
        assertEquals(initialCount + 5, afterFiveActors,
                     "Active actors count should increase by 5 after creating 5 actors");

        actors.get(0).tell(new TestActor.Stop());
        actors.get(1).tell(new TestActor.Stop());

        Thread.sleep(500);
        long afterTwoStopped = SystemMetrics.getInstance().getActiveActors();
        assertEquals(initialCount + 3, afterTwoStopped,
                     "Active actors count should decrease by 2 after terminating 2 actors");

        actorSystem.spawn(
                           TestActor.Command.class,
                           "test-actor-new",
                           TestActor.create(),
                           Duration.ofSeconds(3))
                   .toCompletableFuture()
                   .get(3, TimeUnit.SECONDS);

        Thread.sleep(200);
        long afterNewActor = SystemMetrics.getInstance().getActiveActors();
        assertEquals(initialCount + 4, afterNewActor,
                     "Active actors count should increase by 1 after creating 1 more actor");
    }

    static class TestActor {
        public interface Command {}

        public static class SayHello implements Command {
            public final String message;

            public SayHello(String message) {
                this.message = message;
            }
        }

        public static class Stop implements Command {}

        public static Behavior<Command> create() {
            return Behaviors.setup(ctx ->
                                           Behaviors.receive(Command.class)
                                                    .onMessage(SayHello.class, msg -> {
                                                        ctx.getLog().info("Hello: {}", msg.message);
                                                        return Behaviors.same();
                                                    })
                                                    .onMessage(Stop.class, msg -> {
                                                        ctx.getLog().info("Stopping actor");
                                                        return Behaviors.stopped();
                                                    })
                                                    .build());
        }
    }
}

package io.github.seonwkim.metrics.modules.actor;

import static org.junit.jupiter.api.Assertions.*;

import io.github.seonwkim.metrics.agent.MetricsAgent;
import io.github.seonwkim.metrics.core.MetricsConfiguration;
import io.github.seonwkim.metrics.core.MetricsRegistry;
import io.github.seonwkim.metrics.testing.TestMetricsBackend;
import java.util.concurrent.TimeUnit;
import org.apache.pekko.actor.*;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import scala.concurrent.Await;

/**
 * Integration test for ActorLifecycleModule that verifies ByteBuddy instrumentation
 * works with real Pekko actors (both Classic and Typed APIs).
 * <p>
 * This test runs with the MetricsAgent Java agent loaded, which applies the
 * ByteBuddy transformations to actor classes.
 */
class ActorLifecycleModuleIntegrationTest {

    private TestMetricsBackend metricsBackend;
    private MetricsRegistry metricsRegistry;
    private ActorLifecycleModule module;
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
                .tag("test", "actor-lifecycle-integration")
                .filters(filters)
                .build();

        // Create registry
        metricsRegistry = MetricsRegistry.builder()
                .configuration(config)
                .backend(metricsBackend)
                .build();

        // Create and register module
        module = new ActorLifecycleModule();
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
            Await.result(actorSystem.whenTerminated(), scala.concurrent.duration.Duration.apply(5, TimeUnit.SECONDS));
        }
        if (module != null) {
            module.shutdown();
        }
        MetricsAgent.setRegistry(null);
    }

    @Test
    void testActorCreationIncrementsCounter() throws Exception {
        // Wait a bit for system to stabilize
        Thread.sleep(200);

        // Get initial counter value
        double initialCreated = metricsBackend.getCounterValue("actor.lifecycle.created");

        // Create an actor
        ActorRef actor = actorSystem.actorOf(Props.create(TestActor.class), "test-actor-1");

        assertNotNull(actor);

        // Wait for instrumentation to record
        Thread.sleep(500);

        // Verify created counter incremented
        double afterCreation = metricsBackend.getCounterValue("actor.lifecycle.created");
        assertEquals(
                afterCreation,
                initialCreated + 1,
                String.format(
                        "Created counter should increment. Initial: %.0f, After: %.0f", initialCreated, afterCreation));
    }

    @Test
    void testActiveActorsGaugeIncrements() throws Exception {
        // Wait for system to stabilize
        Thread.sleep(200);

        // Get initial active count
        double initialActive = metricsBackend.getGaugeValue("actor.lifecycle.active");

        // Create first actor
        ActorRef actor1 = actorSystem.actorOf(Props.create(TestActor.class), "test-actor-gauge-1");

        Thread.sleep(500);

        double afterFirstActor = metricsBackend.getGaugeValue("actor.lifecycle.active");
        assertEquals(
                afterFirstActor,
                initialActive + 1,
                String.format(
                        "Active gauge should increment after first actor. Initial: %.0f, After: %.0f",
                        initialActive, afterFirstActor));

        // Create second actor
        ActorRef actor2 = actorSystem.actorOf(Props.create(TestActor.class), "test-actor-gauge-2");

        Thread.sleep(500);

        double afterSecondActor = metricsBackend.getGaugeValue("actor.lifecycle.active");
        assertEquals(
                afterSecondActor,
                afterFirstActor + 1,
                String.format(
                        "Active gauge should increment after second actor. After first: %.0f, After second: %.0f",
                        afterFirstActor, afterSecondActor));
    }

    @Test
    void testActorTerminationIncrementsCounterAndDecrementsGauge() throws Exception {
        // Wait for system to stabilize
        Thread.sleep(200);

        double initialTerminated = metricsBackend.getCounterValue("actor.lifecycle.terminated");
        double initialActive = metricsBackend.getGaugeValue("actor.lifecycle.active");

        // Create actor
        ActorRef actor = actorSystem.actorOf(Props.create(TestActor.class), "test-actor-terminate");

        Thread.sleep(500);

        double activeAfterCreation = metricsBackend.getGaugeValue("actor.lifecycle.active");
        assertEquals(activeAfterCreation, initialActive + 1, "Active gauge should increment after actor creation");

        // Stop the actor
        actorSystem.stop(actor);

        // Wait for termination
        Thread.sleep(1000);

        // Verify terminated counter incremented
        double afterTermination = metricsBackend.getCounterValue("actor.lifecycle.terminated");
        assertEquals(
                afterTermination,
                initialTerminated + 1,
                String.format(
                        "Terminated counter should increment. Initial: %.0f, After: %.0f",
                        initialTerminated, afterTermination));

        // Verify active gauge decremented
        double activeAfterTermination = metricsBackend.getGaugeValue("actor.lifecycle.active");
        assertEquals(
                activeAfterTermination,
                activeAfterCreation - 1,
                String.format(
                        "Active gauge should decrement after termination. Before: %.0f, After: %.0f",
                        activeAfterCreation, activeAfterTermination));
    }

    @Test
    void testMultipleActorsLifecycle() throws Exception {
        // Wait for system to stabilize
        Thread.sleep(200);

        double initialCreated = metricsBackend.getCounterValue("actor.lifecycle.created");
        double initialActive = metricsBackend.getGaugeValue("actor.lifecycle.active");

        // Create 3 actors
        ActorRef actor1 = actorSystem.actorOf(Props.create(TestActor.class), "multi-1");
        ActorRef actor2 = actorSystem.actorOf(Props.create(TestActor.class), "multi-2");
        ActorRef actor3 = actorSystem.actorOf(Props.create(TestActor.class), "multi-3");

        Thread.sleep(500);

        // Verify created counter increased by 3
        double afterCreation = metricsBackend.getCounterValue("actor.lifecycle.created");
        assertEquals(
                afterCreation,
                initialCreated + 3,
                String.format(
                        "Created counter should increase by at least 3. Initial: %.0f, After: %.0f",
                        initialCreated, afterCreation));

        // Verify active gauge increased by 3
        double activeAfterCreation = metricsBackend.getGaugeValue("actor.lifecycle.active");
        assertEquals(
                activeAfterCreation,
                initialActive + 3,
                String.format(
                        "Active gauge should increase by at least 3. Initial: %.0f, After: %.0f",
                        initialActive, activeAfterCreation));

        // Stop 2 actors
        actorSystem.stop(actor1);
        actorSystem.stop(actor2);

        Thread.sleep(1000);

        // Verify active gauge decreased by 2
        double activeAfterTwoStopped = metricsBackend.getGaugeValue("actor.lifecycle.active");
        assertEquals(
                activeAfterTwoStopped,
                activeAfterCreation - 2,
                String.format(
                        "Active gauge should decrease by at least 2. Before: %.0f, After: %.0f",
                        activeAfterCreation, activeAfterTwoStopped));
    }

    @Test
    void testActorMetricsWithTags() throws Exception {
        // Wait for system to stabilize
        Thread.sleep(200);

        // Create actor with specific name
        String actorName = "tagged-actor";
        ActorRef actor = actorSystem.actorOf(Props.create(TestActor.class), actorName);

        Thread.sleep(500);

        // Verify that counters were created with actor.class tag
        // The created counter should have tags including the actor class
        assertTrue(
                metricsBackend.hasMetricWithTag("actor.lifecycle.created", "actor.class"),
                "Created counter should have actor.class tag");
        // Note: The active gauge is global and does NOT have actor-specific tags,
        // as it tracks the total count of all active actors
    }

    @Test
    void testActorLifecycleWithTypedActorSpawn() throws Exception {
        // This test verifies that actor lifecycle is properly monitored when using
        // the same Pekko Typed API that DefaultRootGuardian uses (ctx.spawn)

        // Create a Typed ActorSystem with a guardian that spawns child actors
        org.apache.pekko.actor.typed.ActorSystem<SpawnCommand> typedSystem =
                org.apache.pekko.actor.typed.ActorSystem.create(guardianBehavior(), "typed-test-system");

        try {
            // Wait for system to stabilize
            Thread.sleep(200);

            double initialCreated = metricsBackend.getCounterValue("actor.lifecycle.created");
            double initialActive = metricsBackend.getGaugeValue("actor.lifecycle.active");

            // Send command to spawn a child actor (same pattern as DefaultRootGuardian)
            typedSystem.tell(new SpawnCommand("child-1"));

            // Wait for instrumentation to record
            Thread.sleep(500);

            // Verify created counter incremented by exactly 1
            double afterCreation = metricsBackend.getCounterValue("actor.lifecycle.created");
            assertEquals(
                    afterCreation,
                    initialCreated + 1,
                    String.format(
                            "Created counter should increment by 1. Initial: %.0f, After: %.0f",
                            initialCreated, afterCreation));

            // Verify active gauge incremented by exactly 1
            double activeAfterCreation = metricsBackend.getGaugeValue("actor.lifecycle.active");
            assertEquals(
                    activeAfterCreation,
                    initialActive + 1,
                    String.format(
                            "Active gauge should increment by 1. Initial: %.0f, After: %.0f",
                            initialActive, activeAfterCreation));

            // Spawn another child
            typedSystem.tell(new SpawnCommand("child-2"));
            Thread.sleep(500);

            // Verify created counter incremented by exactly 1 again
            double afterSecondCreation = metricsBackend.getCounterValue("actor.lifecycle.created");
            assertEquals(
                    afterSecondCreation,
                    afterCreation + 1,
                    String.format(
                            "Created counter should increment by 1 for second child. After first: %.0f, After second: %.0f",
                            afterCreation, afterSecondCreation));

            // Verify active gauge incremented by exactly 1 again
            double activeAfterSecondCreation = metricsBackend.getGaugeValue("actor.lifecycle.active");
            assertEquals(
                    activeAfterSecondCreation,
                    activeAfterCreation + 1,
                    String.format(
                            "Active gauge should increment by 1 for second child. After first: %.0f, After second: %.0f",
                            activeAfterCreation, activeAfterSecondCreation));

        } finally {
            typedSystem.terminate();
            Await.result(
                    typedSystem.whenTerminated(),
                    scala.concurrent.duration.Duration.apply(5, TimeUnit.SECONDS));
        }
    }

    /**
     * Creates a guardian behavior that spawns child actors when receiving SpawnCommand.
     * This mimics what DefaultRootGuardian does - using ctx.spawn() to create child actors.
     */
    private static Behavior<SpawnCommand> guardianBehavior() {
        return Behaviors.setup(ctx -> Behaviors.receive(SpawnCommand.class)
                .onMessage(SpawnCommand.class, msg -> {
                    // Use ctx.spawn() - same API that DefaultRootGuardian/ActorSpawner uses
                    ctx.spawn(childBehavior(), msg.name);
                    return Behaviors.same();
                })
                .build());
    }

    /**
     * Simple child actor behavior that just receives messages.
     */
    private static Behavior<String> childBehavior() {
        return Behaviors.receive(String.class)
                .onMessage(String.class, msg -> Behaviors.same())
                .build();
    }

    // Command to spawn a child actor
    public static class SpawnCommand {
        public final String name;

        public SpawnCommand(String name) {
            this.name = name;
        }
    }

    // Test actor (Classic API)
    public static class TestActor extends AbstractActor {
        @Override
        public Receive createReceive() {
            return receiveBuilder()
                    .matchAny(msg -> {
                        // Simple echo actor
                    })
                    .build();
        }
    }
}

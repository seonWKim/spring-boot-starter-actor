package io.github.seonwkim.actor;

import static org.junit.jupiter.api.Assertions.*;

import io.github.seonwkim.actor.autoconfigure.ActorConfiguration;
import java.util.concurrent.TimeUnit;
import org.apache.pekko.actor.ActorCell;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.apache.pekko.actor.typed.javadsl.Adapter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.annotation.DirtiesContext;

/**
 * Integration test for MailboxConfig functionality.
 */
@SpringBootTest(
        classes = {ActorConfiguration.class, MailboxConfigIntegrationTest.TestConfig.class},
        properties = {
            // Basic mailbox type for fromConfig test
            "spring.actor.my-test-mailbox.mailbox-type=org.apache.pekko.dispatch.UnboundedMailbox",
            // Priority mailboxes (require custom mailbox classes with PriorityGenerator)
            "spring.actor.unbounded-priority-mailbox.mailbox-type=io.github.seonwkim.actor.TestPriorityMailboxes$TestUnboundedPriorityMailbox",
            "spring.actor.unbounded-stable-priority-mailbox.mailbox-type=io.github.seonwkim.actor.TestPriorityMailboxes$TestUnboundedStablePriorityMailbox",
            "spring.actor.bounded-priority-mailbox.mailbox-type=io.github.seonwkim.actor.TestPriorityMailboxes$TestBoundedPriorityMailbox",
            "spring.actor.bounded-priority-mailbox.mailbox-capacity=100",
            "spring.actor.bounded-stable-priority-mailbox.mailbox-type=io.github.seonwkim.actor.TestPriorityMailboxes$TestBoundedStablePriorityMailbox",
            "spring.actor.bounded-stable-priority-mailbox.mailbox-capacity=100"
        })
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public class MailboxConfigIntegrationTest {

    @Autowired
    private SpringActorSystem actorSystem;

    @Configuration
    static class TestConfig {
        @Bean
        public ProbeActor mailboxProbeActor() {
            return new ProbeActor();
        }

        @Bean
        public TestActor mailboxTestActor() {
            return new TestActor();
        }
    }

    // Test message types
    public interface TestCommand extends FrameworkCommand {}

    public static class Ping implements TestCommand {
        public final String message;

        public Ping(String message) {
            this.message = message;
        }
    }

    public static class TestActor implements SpringActorWithContext<TestCommand, SpringActorContext> {
        @Override
        public SpringActorBehavior<TestCommand> create(SpringActorContext actorContext) {
            return SpringActorBehavior.builder(TestCommand.class, actorContext)
                    .onMessage(Ping.class, (ctx, msg) -> {
                        ctx.getLog().info("Received: {}", msg.message);
                        return SpringActorBehavior.same();
                    })
                    .build();
        }
    }

    @Test
    void testUnboundedPriorityMailbox() {
        TestPriorityMailboxes.ConstructionTracker.reset();

        SpringActorHandle<TestCommand> actor = actorSystem
                .actor(TestActor.class)
                .withId("unbounded-priority-mailbox-actor")
                .withMailbox(MailboxConfig.fromConfig("unbounded-priority-mailbox"))
                .spawnAndWait();

        assertNotNull(actor);

        // Verify the custom mailbox constructor was called
        assertEquals(
                1,
                TestPriorityMailboxes.ConstructionTracker.getConstructionCount("UnboundedPriorityMailbox"),
                "UnboundedPriorityMailbox constructor should have been called exactly once");

        actor.tell(new Ping("Hello with UnboundedPriorityMailbox"));
    }

    @Test
    void testUnboundedStablePriorityMailbox() {
        TestPriorityMailboxes.ConstructionTracker.reset();

        SpringActorHandle<TestCommand> actor = actorSystem
                .actor(TestActor.class)
                .withId("unbounded-stable-priority-mailbox-actor")
                .withMailbox(MailboxConfig.fromConfig("unbounded-stable-priority-mailbox"))
                .spawnAndWait();

        assertNotNull(actor);

        // Verify the custom mailbox constructor was called
        assertEquals(
                1,
                TestPriorityMailboxes.ConstructionTracker.getConstructionCount("UnboundedStablePriorityMailbox"),
                "UnboundedStablePriorityMailbox constructor should have been called exactly once");

        actor.tell(new Ping("Hello with UnboundedStablePriorityMailbox"));
    }

    @Test
    void testBoundedPriorityMailbox() {
        TestPriorityMailboxes.ConstructionTracker.reset();

        SpringActorHandle<TestCommand> actor = actorSystem
                .actor(TestActor.class)
                .withId("bounded-priority-mailbox-actor")
                .withMailbox(MailboxConfig.fromConfig("bounded-priority-mailbox"))
                .spawnAndWait();

        assertNotNull(actor);

        // Verify the custom mailbox constructor was called with correct capacity
        assertEquals(
                1,
                TestPriorityMailboxes.ConstructionTracker.getConstructionCount("BoundedPriorityMailbox"),
                "BoundedPriorityMailbox constructor should have been called exactly once");
        assertEquals(
                100,
                TestPriorityMailboxes.ConstructionTracker.getLastCapacity("BoundedPriorityMailbox"),
                "BoundedPriorityMailbox should have been constructed with capacity 100");

        actor.tell(new Ping("Hello with BoundedPriorityMailbox"));
    }

    @Test
    void testBoundedStablePriorityMailbox() {
        TestPriorityMailboxes.ConstructionTracker.reset();

        SpringActorHandle<TestCommand> actor = actorSystem
                .actor(TestActor.class)
                .withId("bounded-stable-priority-mailbox-actor")
                .withMailbox(MailboxConfig.fromConfig("bounded-stable-priority-mailbox"))
                .spawnAndWait();

        assertNotNull(actor);

        // Verify the custom mailbox constructor was called with correct capacity
        assertEquals(
                1,
                TestPriorityMailboxes.ConstructionTracker.getConstructionCount("BoundedStablePriorityMailbox"),
                "BoundedStablePriorityMailbox constructor should have been called exactly once");
        assertEquals(
                100,
                TestPriorityMailboxes.ConstructionTracker.getLastCapacity("BoundedStablePriorityMailbox"),
                "BoundedStablePriorityMailbox should have been constructed with capacity 100");

        actor.tell(new Ping("Hello with BoundedStablePriorityMailbox"));
    }

    @Test
    void testBoundedMailboxInvalidCapacity() {
        assertThrows(IllegalArgumentException.class, () -> MailboxConfig.bounded(0));
        assertThrows(IllegalArgumentException.class, () -> MailboxConfig.bounded(-1));
    }

    @Test
    void testFromConfigNullPath() {
        assertThrows(NullPointerException.class, () -> MailboxConfig.fromConfig(null));
    }

    @Test
    void testFromConfigEmptyPath() {
        assertThrows(IllegalArgumentException.class, () -> MailboxConfig.fromConfig(""));
    }

    @Test
    void boundedMailboxIsUsed() throws Exception {
        RunsOn runsOn =
                probe(actorSystem.actor(ProbeActor.class).withId("bounded").withMailbox(MailboxConfig.bounded(50)));

        assertTrue(runsOn.boundedQueue(), runsOn.toString());
        assertEquals(DEFAULT_DISPATCHER, runsOn.dispatcher());
    }

    @Test
    void boundedMailboxIsKeptWithBlockingDispatcher() throws Exception {
        RunsOn runsOn = probe(actorSystem
                .actor(ProbeActor.class)
                .withId("bounded-blocking")
                .withMailbox(MailboxConfig.bounded(50))
                .withBlockingDispatcher());

        assertTrue(runsOn.boundedQueue(), runsOn.toString());
        assertEquals(BLOCKING_DISPATCHER, runsOn.dispatcher());
    }

    @Test
    void configuredMailboxIsKeptWithBlockingDispatcher() throws Exception {
        RunsOn runsOn = probe(actorSystem
                .actor(ProbeActor.class)
                .withId("configured-blocking")
                .withMailbox(MailboxConfig.fromConfig("my-test-mailbox"))
                .withBlockingDispatcher());

        assertEquals("org.apache.pekko.dispatch.UnboundedMailbox$MessageQueue", runsOn.messageQueue());
        assertEquals(BLOCKING_DISPATCHER, runsOn.dispatcher());
    }

    @Test
    void childGetsBoundedMailboxAndBlockingDispatcher() throws Exception {
        SpringActorHandle<TestCommand> parent =
                actorSystem.actor(ProbeActor.class).withId("parent").spawnAndWait();
        SpringActorHandle<TestCommand> child = parent.child(ProbeActor.class)
                .withId("child")
                .withMailbox(MailboxConfig.bounded(25))
                .withDispatcher(DispatcherConfig.blocking())
                .spawn()
                .toCompletableFuture()
                .get(5, TimeUnit.SECONDS);

        RunsOn runsOn = child.ask(new Probe()).execute().toCompletableFuture().get(5, TimeUnit.SECONDS);

        assertTrue(runsOn.boundedQueue(), runsOn.toString());
        assertEquals(BLOCKING_DISPATCHER, runsOn.dispatcher());
    }

    private static final String DEFAULT_DISPATCHER = "pekko.actor.default-dispatcher";
    private static final String BLOCKING_DISPATCHER = "pekko.actor.default-blocking-io-dispatcher";

    private static RunsOn probe(SpringActorSpawnBuilder<ProbeActor, TestCommand> spawn) throws Exception {
        return spawn.spawnAndWait()
                .ask(new Probe())
                .execute()
                .toCompletableFuture()
                .get(5, TimeUnit.SECONDS);
    }

    /** The message queue and dispatcher the actor really runs on. */
    public record RunsOn(String messageQueue, String dispatcher) {
        boolean boundedQueue() {
            return messageQueue.contains("Bounded");
        }
    }

    public static class Probe extends AskCommand<RunsOn> implements TestCommand {}

    public static class ProbeActor implements SpringActorWithContext<TestCommand, SpringActorContext> {
        @Override
        public SpringActorBehavior<TestCommand> create(SpringActorContext actorContext) {
            return SpringActorBehavior.builder(TestCommand.class, actorContext)
                    .onMessage(Probe.class, (ctx, msg) -> {
                        ActorCell cell = (ActorCell) Adapter.toClassic(ctx.unwrap(ActorContext.class));
                        msg.reply(new RunsOn(
                                cell.mailbox().messageQueue().getClass().getName(),
                                cell.dispatcher().id()));
                        return SpringActorBehavior.same();
                    })
                    .build();
        }
    }
}

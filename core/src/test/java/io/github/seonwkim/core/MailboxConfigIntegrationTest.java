package io.github.seonwkim.core;

import java.util.concurrent.CompletionStage;

import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for MailboxConfig functionality.
 */
@SpringBootTest(
        classes = {
            ActorConfiguration.class,
            MailboxConfigIntegrationTest.TestConfig.class
        },
        properties = {
            // Basic mailbox type for fromConfig test
            "spring.actor.my-test-mailbox.mailbox-type=org.apache.pekko.dispatch.UnboundedMailbox",
            // Priority mailboxes (require custom mailbox classes with PriorityGenerator)
            "spring.actor.unbounded-priority-mailbox.mailbox-type=io.github.seonwkim.core.TestPriorityMailboxes$TestUnboundedPriorityMailbox",
            "spring.actor.unbounded-stable-priority-mailbox.mailbox-type=io.github.seonwkim.core.TestPriorityMailboxes$TestUnboundedStablePriorityMailbox",
            "spring.actor.bounded-priority-mailbox.mailbox-type=io.github.seonwkim.core.TestPriorityMailboxes$TestBoundedPriorityMailbox",
            "spring.actor.bounded-priority-mailbox.mailbox-capacity=100",
            "spring.actor.bounded-stable-priority-mailbox.mailbox-type=io.github.seonwkim.core.TestPriorityMailboxes$TestBoundedStablePriorityMailbox",
            "spring.actor.bounded-stable-priority-mailbox.mailbox-capacity=100"
        })
public class MailboxConfigIntegrationTest {

    @Autowired
    private SpringActorSystem actorSystem;

    @Configuration
    static class TestConfig {
        @Bean
        public TestActor testActor() {
            return new TestActor();
        }

        @Bean
        public TestParentActor testParentActor() {
            return new TestParentActor();
        }

        @Bean
        public TestChildActor testChildActor() {
            return new TestChildActor();
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

    @Component
    public static class TestActor implements SpringActorWithContext<TestCommand, SpringActorContext> {
        @Override
        public SpringActorBehavior<TestCommand> create(SpringActorContext actorContext) {
            return SpringActorBehavior.builder(TestCommand.class, actorContext)
                    .onMessage(Ping.class, (ctx, msg) -> {
                        ctx.getLog().info("Received: {}", msg.message);
                        return Behaviors.same();
                    })
                    .build();
        }
    }

    @Component
    public static class TestParentActor implements SpringActorWithContext<TestCommand, SpringActorContext> {
        @Override
        public SpringActorBehavior<TestCommand> create(SpringActorContext actorContext) {
            return SpringActorBehavior.builder(TestCommand.class, actorContext)
                    .onMessage(Ping.class, (ctx, msg) -> {
                        ctx.getLog().info("Parent received: {}", msg.message);
                        return Behaviors.same();
                    })
                    .build();
        }
    }

    @Component
    public static class TestChildActor implements SpringActorWithContext<TestCommand, SpringActorContext> {
        @Override
        public SpringActorBehavior<TestCommand> create(SpringActorContext actorContext) {
            return SpringActorBehavior.builder(TestCommand.class, actorContext)
                    .onMessage(Ping.class, (ctx, msg) -> {
                        ctx.getLog().info("Child received: {}", msg.message);
                        return Behaviors.same();
                    })
                    .build();
        }
    }


    @Test
    void testDefaultMailbox() {
        SpringActorRef<TestCommand> actor = actorSystem.actor(TestActor.class)
                .withId("default-mailbox-actor")
                .withMailbox(MailboxConfig.defaultMailbox())
                .spawnAndWait();

        assertNotNull(actor);
        actor.tell(new Ping("Hello with default mailbox"));
    }

    @Test
    void testBoundedMailbox() {
        SpringActorRef<TestCommand> actor = actorSystem.actor(TestActor.class)
                .withId("bounded-mailbox-actor")
                .withMailbox(MailboxConfig.bounded(100))
                .spawnAndWait();

        assertNotNull(actor);
        actor.tell(new Ping("Hello with bounded mailbox"));
    }

    @Test
    void testFromConfigMailbox() {
        SpringActorRef<TestCommand> actor = actorSystem.actor(TestActor.class)
                .withId("config-mailbox-actor")
                .withMailbox(MailboxConfig.fromConfig("my-test-mailbox"))
                .spawnAndWait();

        assertNotNull(actor);
        actor.tell(new Ping("Hello with config mailbox"));
    }

    @Test
    void testCombinedWithDispatcher() {
        SpringActorRef<TestCommand> actor = actorSystem.actor(TestActor.class)
                .withId("combined-actor")
                .withMailbox(MailboxConfig.bounded(50))
                .withBlockingDispatcher()
                .spawnAndWait();

        assertNotNull(actor);
        actor.tell(new Ping("Hello with bounded mailbox and blocking dispatcher"));
    }

    @Test
    void testChildActorWithMailbox() throws Exception {
        // Spawn parent actor
        SpringActorRef<TestCommand> parent = actorSystem.actor(TestParentActor.class)
                .withId("parent-with-child")
                .spawnAndWait();

        assertNotNull(parent);

        // Spawn child actor with bounded mailbox
        CompletionStage<SpringActorRef<TestCommand>> childFuture = parent.child(TestChildActor.class)
                .withId("bounded-child")
                .withMailbox(MailboxConfig.bounded(25))
                .spawn();

        SpringActorRef<TestCommand> child = childFuture.toCompletableFuture().get();
        assertNotNull(child);

        child.tell(new Ping("Hello from child with bounded mailbox"));
    }

    @Test
    void testUnboundedPriorityMailbox() {
        TestPriorityMailboxes.ConstructionTracker.reset();

        SpringActorRef<TestCommand> actor = actorSystem.actor(TestActor.class)
                .withId("unbounded-priority-mailbox-actor")
                .withMailbox(MailboxConfig.fromConfig("unbounded-priority-mailbox"))
                .spawnAndWait();

        assertNotNull(actor);

        // Verify the custom mailbox constructor was called
        assertEquals(1, TestPriorityMailboxes.ConstructionTracker.getConstructionCount("UnboundedPriorityMailbox"),
                "UnboundedPriorityMailbox constructor should have been called exactly once");

        actor.tell(new Ping("Hello with UnboundedPriorityMailbox"));
    }

    @Test
    void testUnboundedStablePriorityMailbox() {
        TestPriorityMailboxes.ConstructionTracker.reset();

        SpringActorRef<TestCommand> actor = actorSystem.actor(TestActor.class)
                .withId("unbounded-stable-priority-mailbox-actor")
                .withMailbox(MailboxConfig.fromConfig("unbounded-stable-priority-mailbox"))
                .spawnAndWait();

        assertNotNull(actor);

        // Verify the custom mailbox constructor was called
        assertEquals(1, TestPriorityMailboxes.ConstructionTracker.getConstructionCount("UnboundedStablePriorityMailbox"),
                "UnboundedStablePriorityMailbox constructor should have been called exactly once");

        actor.tell(new Ping("Hello with UnboundedStablePriorityMailbox"));
    }

    @Test
    void testBoundedPriorityMailbox() {
        TestPriorityMailboxes.ConstructionTracker.reset();

        SpringActorRef<TestCommand> actor = actorSystem.actor(TestActor.class)
                .withId("bounded-priority-mailbox-actor")
                .withMailbox(MailboxConfig.fromConfig("bounded-priority-mailbox"))
                .spawnAndWait();

        assertNotNull(actor);

        // Verify the custom mailbox constructor was called with correct capacity
        assertEquals(1, TestPriorityMailboxes.ConstructionTracker.getConstructionCount("BoundedPriorityMailbox"),
                "BoundedPriorityMailbox constructor should have been called exactly once");
        assertEquals(100, TestPriorityMailboxes.ConstructionTracker.getLastCapacity("BoundedPriorityMailbox"),
                "BoundedPriorityMailbox should have been constructed with capacity 100");

        actor.tell(new Ping("Hello with BoundedPriorityMailbox"));
    }

    @Test
    void testBoundedStablePriorityMailbox() {
        TestPriorityMailboxes.ConstructionTracker.reset();

        SpringActorRef<TestCommand> actor = actorSystem.actor(TestActor.class)
                .withId("bounded-stable-priority-mailbox-actor")
                .withMailbox(MailboxConfig.fromConfig("bounded-stable-priority-mailbox"))
                .spawnAndWait();

        assertNotNull(actor);

        // Verify the custom mailbox constructor was called with correct capacity
        assertEquals(1, TestPriorityMailboxes.ConstructionTracker.getConstructionCount("BoundedStablePriorityMailbox"),
                "BoundedStablePriorityMailbox constructor should have been called exactly once");
        assertEquals(100, TestPriorityMailboxes.ConstructionTracker.getLastCapacity("BoundedStablePriorityMailbox"),
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
}

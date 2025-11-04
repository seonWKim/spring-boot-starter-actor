package io.github.seonwkim.core;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.seonwkim.core.SpringActorSystemTest.TestHelloActor.SayHello;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import org.springframework.test.context.TestPropertySource;

class SpringActorSystemTest {

    @Component
    static class TestHelloActor implements SpringActor<TestHelloActor.Command> {

        public interface Command {}

        public static class SayHello implements TestHelloActor.Command {
            private final ActorRef<Object> replyTo;

            public SayHello(ActorRef<Object> replyTo) {
                this.replyTo = replyTo;
            }
        }

        @Override
        public SpringActorBehavior<Command> create(SpringActorContext id) {
            return SpringActorBehavior.builder(Command.class, id)
                    .onMessage(SayHello.class, (ctx, msg) -> {
                        msg.replyTo.tell("hello world!!");
                        return Behaviors.same();
                    })
                    .build();
        }
    }

    @Component
    static class DispatcherTestActor implements SpringActor<DispatcherTestActor.Command> {

        public interface Command {}

        public static class GetDispatcherName implements Command {
            private final ActorRef<String> replyTo;

            public GetDispatcherName(ActorRef<String> replyTo) {
                this.replyTo = replyTo;
            }
        }

        @Override
        public SpringActorBehavior<Command> create(SpringActorContext id) {
            return SpringActorBehavior.builder(Command.class, id)
                    .onMessage(GetDispatcherName.class, (ctx, msg) -> {
                        // Get the dispatcher name from the execution context
                        String dispatcherName = ctx.getExecutionContext().toString();
                        msg.replyTo.tell(dispatcherName);
                        return Behaviors.same();
                    })
                    .build();
        }
    }

    @Component
    static class CustomActorContextActor
            implements SpringActorWithContext<CustomActorContextActor.Command, CustomActorContext> {

        public interface Command {}

        public static class SayHello implements CustomActorContextActor.Command {
            private final ActorRef<Object> replyTo;

            public SayHello(ActorRef<Object> replyTo) {
                this.replyTo = replyTo;
            }
        }

        @Override
        public SpringActorBehavior<Command> create(CustomActorContext context) {
            return SpringActorBehavior.builder(Command.class, context)
                    .onMessage(SayHello.class, (ctx, msg) -> {
                        msg.replyTo.tell(context.actorId());
                        return Behaviors.same();
                    })
                    .build();
        }
    }

    /**
     * Test actor that demonstrates building an actor WITHOUT using onCreate().
     * This uses the default state type (ActorContext) and handles messages directly.
     */
    @Component
    static class SimpleActorWithoutOnCreate implements SpringActor<SimpleActorWithoutOnCreate.Command> {

        public interface Command {}

        public static class Increment implements Command {
            private final ActorRef<Integer> replyTo;

            public Increment(ActorRef<Integer> replyTo) {
                this.replyTo = replyTo;
            }
        }

        @Override
        public SpringActorBehavior<Command> create(SpringActorContext actorContext) {
            // Using a simple closure to maintain state without onCreate
            final int[] counter = {0};

            return SpringActorBehavior.builder(Command.class, actorContext)
                    // No onCreate() - message handlers work directly with ActorContext
                    .onMessage(Increment.class, (ctx, msg) -> {
                        counter[0]++;
                        ctx.getLog().info("Counter for {} incremented to {}", actorContext.actorId(), counter[0]);
                        msg.replyTo.tell(counter[0]);
                        return Behaviors.same();
                    })
                    .build();
        }
    }

    static class CustomActorContext extends SpringActorContext {

        private final String actorId;

        CustomActorContext(String actorId) {
            this.actorId = actorId + "-custom";
        }

        @Override
        public String actorId() {
            return actorId;
        }
    }

    @SpringBootApplication(scanBasePackages = "io.github.seonwkim.core")
    static class TestApp {}

    @Nested
    @SpringBootTest(classes = TestApp.class)
    @TestPropertySource(properties = {"spring.actor.pekko.loglevel=INFO", "spring.actor.pekko.actor.provider=local"})
    class SimpleTest {

        @Test
        void spawnAndStopActors(ApplicationContext context) throws Exception {
            assertTrue(context.containsBean("actorSystem"), "ActorSystem bean should exist");
            SpringActorSystem actorSystem = context.getBean(SpringActorSystem.class);

            final String actorId = "test-actor";
            final SpringActorRef<TestHelloActor.Command> actorRef =
                    actorSystem.actor(TestHelloActor.class).withId(actorId).spawnAndWait();

            assertThat(actorRef).isNotNull();

            assertEquals(
                    "hello world!!",
                    actorRef.askBuilder(SayHello::new)
                            .withTimeout(Duration.ofSeconds(5))
                            .execute()
                            .toCompletableFuture()
                            .get());

            // Stop the actor using the simplified API (fire-and-forget)
            actorRef.stop();
        }

        @Test
        void customActorContext(ApplicationContext context) throws Exception {
            assertTrue(context.containsBean("actorSystem"), "ActorSystem bean should exist");
            SpringActorSystem actorSystem = context.getBean(SpringActorSystem.class);

            final String actorId = "test-actor";
            final SpringActorContext actorContext = new CustomActorContext(actorId);
            final SpringActorRef<CustomActorContextActor.Command> actorRef = actorSystem
                    .actor(CustomActorContextActor.class)
                    .withContext(actorContext)
                    .spawnAndWait();
            assertThat(actorRef).isNotNull();
            assertEquals(
                    actorRef.askBuilder(CustomActorContextActor.SayHello::new)
                            .withTimeout(Duration.ofSeconds(5))
                            .execute()
                            .toCompletableFuture()
                            .get(),
                    actorContext.actorId());
        }

        @Test
        void existsReturnsFalseForNonExistentActor(ApplicationContext context) throws Exception {
            SpringActorSystem actorSystem = context.getBean(SpringActorSystem.class);

            // Check that an actor that was never spawned doesn't exist
            boolean exists = actorSystem
                    .exists(TestHelloActor.class, "non-existent-actor")
                    .toCompletableFuture()
                    .get(5, TimeUnit.SECONDS);

            assertThat(exists).isFalse();
        }

        @Test
        void existsReturnsTrueForSpawnedActor(ApplicationContext context) throws Exception {
            SpringActorSystem actorSystem = context.getBean(SpringActorSystem.class);

            final String actorId = "exists-test-actor";

            // Verify actor doesn't exist before spawning
            assertThat(actorSystem
                            .exists(TestHelloActor.class, actorId)
                            .toCompletableFuture()
                            .get(5, TimeUnit.SECONDS))
                    .isFalse();

            // Spawn the actor
            actorSystem.actor(TestHelloActor.class).withId(actorId).spawnAndWait();

            // Verify actor exists after spawning
            assertThat(actorSystem
                            .exists(TestHelloActor.class, actorId)
                            .toCompletableFuture()
                            .get(5, TimeUnit.SECONDS))
                    .isTrue();
        }

        @Test
        void getReturnsNullForNonExistentActor(ApplicationContext context) throws Exception {
            SpringActorSystem actorSystem = context.getBean(SpringActorSystem.class);

            // Get an actor that was never spawned
            SpringActorRef<TestHelloActor.Command> actorRef = actorSystem
                    .get(TestHelloActor.class, "non-existent-actor")
                    .toCompletableFuture()
                    .get(5, TimeUnit.SECONDS);

            assertThat(actorRef).isNull();
        }

        @Test
        void getReturnsValidRefForSpawnedActor(ApplicationContext context) throws Exception {
            SpringActorSystem actorSystem = context.getBean(SpringActorSystem.class);

            final String actorId = "get-test-actor";

            // Spawn the actor
            SpringActorRef<TestHelloActor.Command> spawnedRef =
                    actorSystem.actor(TestHelloActor.class).withId(actorId).spawnAndWait();
            assertThat(spawnedRef).isNotNull();

            // Get the actor using get()
            SpringActorRef<TestHelloActor.Command> retrievedRef = actorSystem
                    .get(TestHelloActor.class, actorId)
                    .toCompletableFuture()
                    .get(5, TimeUnit.SECONDS);

            assertThat(retrievedRef).isNotNull();

            // Verify we can use the retrieved ref to send messages
            Object response = retrievedRef
                    .askBuilder(SayHello::new)
                    .withTimeout(Duration.ofSeconds(5))
                    .execute()
                    .toCompletableFuture()
                    .get();
            assertEquals("hello world!!", response);
        }

        @Test
        void existsReturnsFalseAfterActorStopped(ApplicationContext context) throws Exception {
            SpringActorSystem actorSystem = context.getBean(SpringActorSystem.class);

            final String actorId = "stop-test-actor";

            // Spawn the actor
            SpringActorRef<TestHelloActor.Command> actorRef =
                    actorSystem.actor(TestHelloActor.class).withId(actorId).spawnAndWait();

            // Verify actor exists
            assertThat(actorSystem
                            .exists(TestHelloActor.class, actorId)
                            .toCompletableFuture()
                            .get(5, TimeUnit.SECONDS))
                    .isTrue();

            // Stop the actor
            actorRef.stop();

            // Wait a bit for the actor to stop
            Thread.sleep(100);

            // Verify actor no longer exists
            assertThat(actorSystem
                            .exists(TestHelloActor.class, actorId)
                            .toCompletableFuture()
                            .get(5, TimeUnit.SECONDS))
                    .isFalse();
        }

        @Test
        void getAndExistsWorkWithDifferentActorIds(ApplicationContext context) throws Exception {
            SpringActorSystem actorSystem = context.getBean(SpringActorSystem.class);

            // Spawn multiple actors with different IDs
            actorSystem.actor(TestHelloActor.class).withId("actor-1").spawnAndWait();
            actorSystem.actor(TestHelloActor.class).withId("actor-2").spawnAndWait();

            // Verify each exists independently
            assertThat(actorSystem
                            .exists(TestHelloActor.class, "actor-1")
                            .toCompletableFuture()
                            .get(5, TimeUnit.SECONDS))
                    .isTrue();
            assertThat(actorSystem
                            .exists(TestHelloActor.class, "actor-2")
                            .toCompletableFuture()
                            .get(5, TimeUnit.SECONDS))
                    .isTrue();
            assertThat(actorSystem
                            .exists(TestHelloActor.class, "actor-3")
                            .toCompletableFuture()
                            .get(5, TimeUnit.SECONDS))
                    .isFalse();

            // Verify get works for each
            assertThat(actorSystem
                            .get(TestHelloActor.class, "actor-1")
                            .toCompletableFuture()
                            .get(5, TimeUnit.SECONDS))
                    .isNotNull();
            assertThat(actorSystem
                            .get(TestHelloActor.class, "actor-2")
                            .toCompletableFuture()
                            .get(5, TimeUnit.SECONDS))
                    .isNotNull();
            assertThat(actorSystem
                            .get(TestHelloActor.class, "actor-3")
                            .toCompletableFuture()
                            .get(5, TimeUnit.SECONDS))
                    .isNull();
        }

        @Test
        void actorWithoutOnCreateWorks(ApplicationContext context) throws Exception {
            SpringActorSystem actorSystem = context.getBean(SpringActorSystem.class);

            final String actorId = "counter-actor";
            final SpringActorRef<SimpleActorWithoutOnCreate.Command> actorRef = actorSystem
                    .actor(SimpleActorWithoutOnCreate.class)
                    .withId(actorId)
                    .spawnAndWait();

            assertThat(actorRef).isNotNull();

            // Send increment messages and verify counter increases
            Integer count1 = actorRef.askBuilder(SimpleActorWithoutOnCreate.Increment::new)
                    .withTimeout(Duration.ofSeconds(5))
                    .execute()
                    .toCompletableFuture()
                    .get();
            assertEquals(1, count1);

            Integer count2 = actorRef.askBuilder(SimpleActorWithoutOnCreate.Increment::new)
                    .withTimeout(Duration.ofSeconds(5))
                    .execute()
                    .toCompletableFuture()
                    .get();
            assertEquals(2, count2);

            Integer count3 = actorRef.askBuilder(SimpleActorWithoutOnCreate.Increment::new)
                    .withTimeout(Duration.ofSeconds(5))
                    .execute()
                    .toCompletableFuture()
                    .get();
            assertEquals(3, count3);
        }

        @Test
        void spawnActorWithDefaultDispatcher(ApplicationContext context) throws Exception {
            SpringActorSystem actorSystem = context.getBean(SpringActorSystem.class);

            final String actorId = "default-dispatcher-actor";
            final SpringActorRef<DispatcherTestActor.Command> actorRef = actorSystem
                    .actor(DispatcherTestActor.class)
                    .withId(actorId)
                    .withDefaultDispatcher()
                    .spawnAndWait();

            assertThat(actorRef).isNotNull();

            String dispatcherName = actorRef.askBuilder(DispatcherTestActor.GetDispatcherName::new)
                    .withTimeout(Duration.ofSeconds(5))
                    .execute()
                    .toCompletableFuture()
                    .get();

            // Default dispatcher should contain "pekko.actor.default-dispatcher"
            assertThat(dispatcherName).contains("pekko.actor.default-dispatcher");
        }

        @Test
        void spawnActorWithBlockingDispatcher(ApplicationContext context) throws Exception {
            SpringActorSystem actorSystem = context.getBean(SpringActorSystem.class);

            final String actorId = "blocking-dispatcher-actor";
            final SpringActorRef<DispatcherTestActor.Command> actorRef = actorSystem
                    .actor(DispatcherTestActor.class)
                    .withId(actorId)
                    .withBlockingDispatcher()
                    .spawnAndWait();

            assertThat(actorRef).isNotNull();

            String dispatcherName = actorRef.askBuilder(DispatcherTestActor.GetDispatcherName::new)
                    .withTimeout(Duration.ofSeconds(5))
                    .execute()
                    .toCompletableFuture()
                    .get();

            // Blocking dispatcher should contain "pekko.actor.default-blocking-io-dispatcher"
            assertThat(dispatcherName).contains("pekko.actor.default-blocking-io-dispatcher");
        }

        @Test
        void spawnActorWithDispatcherSameAsParent(ApplicationContext context) throws Exception {
            SpringActorSystem actorSystem = context.getBean(SpringActorSystem.class);

            final String actorId = "same-as-parent-dispatcher-actor";
            final SpringActorRef<DispatcherTestActor.Command> actorRef = actorSystem
                    .actor(DispatcherTestActor.class)
                    .withId(actorId)
                    .withDispatcherSameAsParent()
                    .spawnAndWait();

            assertThat(actorRef).isNotNull();

            String dispatcherName = actorRef.askBuilder(DispatcherTestActor.GetDispatcherName::new)
                    .withTimeout(Duration.ofSeconds(5))
                    .execute()
                    .toCompletableFuture()
                    .get();

            // Same-as-parent dispatcher should be the same as default (since spawned from root guardian)
            assertThat(dispatcherName).contains("pekko.actor.default-dispatcher");
        }
    }
}

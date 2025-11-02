package io.github.seonwkim.core;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.seonwkim.core.SpringActorSystemTest.TestHelloActor.SayHello;
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
                    actorSystem.actor(TestHelloActor.class).withId(actorId).startAndWait();

            assertThat(actorRef).isNotNull();

            assertEquals(
                    "hello world!!",
                    actorRef.askBuilder(SayHello::new)
                            .withTimeout(java.time.Duration.ofSeconds(5))
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
                    .startAndWait();
            assertThat(actorRef).isNotNull();
            assertEquals(
                    actorRef.askBuilder(CustomActorContextActor.SayHello::new)
                            .withTimeout(java.time.Duration.ofSeconds(5))
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
            actorSystem.actor(TestHelloActor.class).withId(actorId).startAndWait();

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
                    actorSystem.actor(TestHelloActor.class).withId(actorId).startAndWait();
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
                    .withTimeout(java.time.Duration.ofSeconds(5))
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
                    actorSystem.actor(TestHelloActor.class).withId(actorId).startAndWait();

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
            actorSystem.actor(TestHelloActor.class).withId("actor-1").startAndWait();
            actorSystem.actor(TestHelloActor.class).withId("actor-2").startAndWait();

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
    }
}

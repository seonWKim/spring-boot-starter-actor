package io.github.seonwkim.core;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.seonwkim.core.SpringActorSystemTest.TestHelloActor.SayHello;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
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
    static class TestHelloActor implements SpringActor<TestHelloActor, TestHelloActor.Command> {

        public interface Command {}

        public static class SayHello implements TestHelloActor.Command {
            private final ActorRef<Object> replyTo;

            public SayHello(ActorRef<Object> replyTo) {
                this.replyTo = replyTo;
            }
        }

        @Override
        public Behavior<Command> create(SpringActorContext id) {
            return Behaviors.setup(ctx -> Behaviors.receive(Command.class)
                    .onMessage(SayHello.class, msg -> {
                        msg.replyTo.tell("hello world!!");
                        return Behaviors.same();
                    })
                    .build());
        }
    }

    @Component
    static class CustomActorContextActor
            implements SpringActorWithContext<
                    CustomActorContextActor, CustomActorContextActor.Command, CustomActorContext> {

        public interface Command {}

        public static class SayHello implements CustomActorContextActor.Command {
            private final ActorRef<Object> replyTo;

            public SayHello(ActorRef<Object> replyTo) {
                this.replyTo = replyTo;
            }
        }

        @Override
        public Behavior<Command> create(CustomActorContext context) {
            return Behaviors.setup(ctx -> Behaviors.receive(Command.class)
                    .onMessage(SayHello.class, msg -> {
                        msg.replyTo.tell(context.actorId());
                        return Behaviors.same();
                    })
                    .build());
        }
    }

    static class CustomActorContext implements SpringActorContext {

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
        void spawnAndStopActors(ApplicationContext context) {
            assertTrue(context.containsBean("actorSystem"), "ActorSystem bean should exist");
            SpringActorSystem actorSystem = context.getBean(SpringActorSystem.class);

            final String actorId = "test-actor";
            final SpringActorRef<TestHelloActor.Command> actorRef =
                    actorSystem.spawn(TestHelloActor.class).withId(actorId).startAndWait();

            assertThat(actorRef).isNotNull();

            assertEquals(actorRef.ask(SayHello::new).toCompletableFuture().join(), "hello world!!");

            // Stop the actor using the simplified API (fire-and-forget)
            actorRef.stop();
        }

        @Test
        void customActorContext(ApplicationContext context) {
            assertTrue(context.containsBean("actorSystem"), "ActorSystem bean should exist");
            SpringActorSystem actorSystem = context.getBean(SpringActorSystem.class);

            final String actorId = "test-actor";
            final SpringActorContext actorContext = new CustomActorContext(actorId);
            final SpringActorRef<CustomActorContextActor.Command> actorRef = actorSystem
                    .spawn(CustomActorContextActor.class)
                    .withContext(actorContext)
                    .startAndWait();
            assertThat(actorRef).isNotNull();
            assertEquals(
                    actorRef.ask(CustomActorContextActor.SayHello::new)
                            .toCompletableFuture()
                            .join(),
                    actorContext.actorId());
        }

        @Test
        void existsReturnsFalseForNonExistentActor(ApplicationContext context) {
            SpringActorSystem actorSystem = context.getBean(SpringActorSystem.class);

            // Check that an actor that was never spawned doesn't exist
            boolean exists = actorSystem.exists(TestHelloActor.class, "non-existent-actor")
                    .toCompletableFuture()
                    .join();

            assertThat(exists).isFalse();
        }

        @Test
        void existsReturnsTrueForSpawnedActor(ApplicationContext context) {
            SpringActorSystem actorSystem = context.getBean(SpringActorSystem.class);

            final String actorId = "exists-test-actor";

            // Verify actor doesn't exist before spawning
            assertThat(actorSystem.exists(TestHelloActor.class, actorId).toCompletableFuture().join())
                    .isFalse();

            // Spawn the actor
            actorSystem.spawn(TestHelloActor.class).withId(actorId).startAndWait();

            // Verify actor exists after spawning
            assertThat(actorSystem.exists(TestHelloActor.class, actorId).toCompletableFuture().join())
                    .isTrue();
        }

        @Test
        void getReturnsNullForNonExistentActor(ApplicationContext context) {
            SpringActorSystem actorSystem = context.getBean(SpringActorSystem.class);

            // Get an actor that was never spawned
            SpringActorRef<TestHelloActor.Command> actorRef = actorSystem
                    .get(TestHelloActor.class, "non-existent-actor")
                    .toCompletableFuture()
                    .join();

            assertThat(actorRef).isNull();
        }

        @Test
        void getReturnsValidRefForSpawnedActor(ApplicationContext context) {
            SpringActorSystem actorSystem = context.getBean(SpringActorSystem.class);

            final String actorId = "get-test-actor";

            // Spawn the actor
            SpringActorRef<TestHelloActor.Command> spawnedRef =
                    actorSystem.spawn(TestHelloActor.class).withId(actorId).startAndWait();
            assertThat(spawnedRef).isNotNull();

            // Get the actor using get()
            SpringActorRef<TestHelloActor.Command> retrievedRef = actorSystem
                    .get(TestHelloActor.class, actorId)
                    .toCompletableFuture()
                    .join();

            assertThat(retrievedRef).isNotNull();

            // Verify we can use the retrieved ref to send messages
            Object response = retrievedRef.ask(SayHello::new).toCompletableFuture().join();
            assertEquals("hello world!!", response);
        }

        @Test
        void existsReturnsFalseAfterActorStopped(ApplicationContext context) throws InterruptedException {
            SpringActorSystem actorSystem = context.getBean(SpringActorSystem.class);

            final String actorId = "stop-test-actor";

            // Spawn the actor
            SpringActorRef<TestHelloActor.Command> actorRef =
                    actorSystem.spawn(TestHelloActor.class).withId(actorId).startAndWait();

            // Verify actor exists
            assertThat(actorSystem.exists(TestHelloActor.class, actorId).toCompletableFuture().join())
                    .isTrue();

            // Stop the actor
            actorRef.stop();

            // Wait a bit for the actor to stop
            Thread.sleep(100);

            // Verify actor no longer exists
            assertThat(actorSystem.exists(TestHelloActor.class, actorId).toCompletableFuture().join())
                    .isFalse();
        }

        @Test
        void getAndExistsWorkWithDifferentActorIds(ApplicationContext context) {
            SpringActorSystem actorSystem = context.getBean(SpringActorSystem.class);

            // Spawn multiple actors with different IDs
            actorSystem.spawn(TestHelloActor.class).withId("actor-1").startAndWait();
            actorSystem.spawn(TestHelloActor.class).withId("actor-2").startAndWait();

            // Verify each exists independently
            assertThat(actorSystem.exists(TestHelloActor.class, "actor-1").toCompletableFuture().join())
                    .isTrue();
            assertThat(actorSystem.exists(TestHelloActor.class, "actor-2").toCompletableFuture().join())
                    .isTrue();
            assertThat(actorSystem.exists(TestHelloActor.class, "actor-3").toCompletableFuture().join())
                    .isFalse();

            // Verify get works for each
            assertThat(actorSystem.get(TestHelloActor.class, "actor-1").toCompletableFuture().join())
                    .isNotNull();
            assertThat(actorSystem.get(TestHelloActor.class, "actor-2").toCompletableFuture().join())
                    .isNotNull();
            assertThat(actorSystem.get(TestHelloActor.class, "actor-3").toCompletableFuture().join())
                    .isNull();
        }
    }
}

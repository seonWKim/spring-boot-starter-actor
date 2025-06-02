package io.github.seonwkim.core;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.seonwkim.core.RootGuardian.ActorNotFound;
import io.github.seonwkim.core.RootGuardian.Stopped;
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
    static class TestHelloActor implements SpringActor<TestHelloActor.Command> {
        @Override
        public Class<Command> commandClass() {
            return Command.class;
        }

        public interface Command {}

        public static class SayHello implements TestHelloActor.Command {
            private final ActorRef<Object> replyTo;

            public SayHello(ActorRef<Object> replyTo) {
                this.replyTo = replyTo;
            }
        }

        @Override
        public Behavior<Command> create(SpringActorContext id) {
            return Behaviors.setup(
                    ctx ->
                            Behaviors.receive(Command.class)
                                     .onMessage(
                                             SayHello.class,
                                             msg -> {
                                                 msg.replyTo.tell("hello world!!");
                                                 return Behaviors.same();
                                             })
                                     .build());
        }
    }

    @Component
    static class CustomActorContextActor implements SpringActor<CustomActorContextActor.Command> {
        @Override
        public Class<Command> commandClass() {
            return Command.class;
        }

        public interface Command {}

        public static class SayHello implements CustomActorContextActor.Command {
            private final ActorRef<Object> replyTo;

            public SayHello(ActorRef<Object> replyTo) {
                this.replyTo = replyTo;
            }
        }

        @Override
        public Behavior<Command> create(SpringActorContext context) {
            return Behaviors.setup(
                    ctx ->
                            Behaviors.receive(Command.class)
                                     .onMessage(
                                             SayHello.class,
                                             msg -> {
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
    @TestPropertySource(
            properties = {
                    "spring.actor.pekko.loglevel=INFO",
                    "spring.actor.pekko.actor.provider=local"
            })
    class SimpleTest {

        @Test
        void spawnAndStopActors(ApplicationContext context) {
            assertTrue(context.containsBean("actorSystem"), "ActorSystem bean should exist");
            SpringActorSystem actorSystem = context.getBean(SpringActorSystem.class);

            final String actorId = "test-actor";
            final SpringActorSpawnContext<TestHelloActor.Command> spawnContext =
                    new SpringActorSpawnContext.Builder<TestHelloActor.Command>()
                            .commandClass(TestHelloActor.Command.class)
                            .actorId(actorId)
                            .build();
            final SpringActorRef<TestHelloActor.Command> actorRef = actorSystem.spawn(spawnContext).toCompletableFuture().join();
            assertThat(actorRef).isNotNull();

            assertEquals(actorRef.ask(SayHello::new).toCompletableFuture().join(), "hello world!!");
            assertEquals(
                    actorSystem
                            .stop(TestHelloActor.Command.class, actorId)
                            .toCompletableFuture()
                            .join()
                            .getClass(),
                    Stopped.class);
            assertEquals(
                    actorSystem
                            .stop(TestHelloActor.Command.class, actorId)
                            .toCompletableFuture()
                            .join()
                            .getClass(),
                    ActorNotFound.class);
        }


        @Test
        void customActorContext(ApplicationContext context) {
            assertTrue(context.containsBean("actorSystem"), "ActorSystem bean should exist");
            SpringActorSystem actorSystem = context.getBean(SpringActorSystem.class);

            final String actorId = "test-actor";
            final SpringActorContext actorContext = new CustomActorContext(actorId);
            final SpringActorSpawnContext<CustomActorContextActor.Command> spawnContext =
                    new SpringActorSpawnContext.Builder<CustomActorContextActor.Command>()
                            .commandClass(CustomActorContextActor.Command.class)
                            .actorContext(actorContext)
                            .build();
            final SpringActorRef<CustomActorContextActor.Command> actorRef =
                    actorSystem.spawn(spawnContext).toCompletableFuture().join();
            assertThat(actorRef).isNotNull();
            assertEquals(actorRef.ask(CustomActorContextActor.SayHello::new).toCompletableFuture().join(), actorContext.actorId());
        }
    }
}

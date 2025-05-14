package io.github.seonwkim.core;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

import io.github.seonwkim.core.SpringActorSystemTest.TestHelloActor.SayHello;
import io.github.seonwkim.core.RootGuardian.ActorNotFound;
import io.github.seonwkim.core.RootGuardian.Stopped;

class SpringActorSystemTest {

    @Component
    static class TestHelloActor implements SpringActor {
        @Override
        public Class<?> commandClass() {
            return Command.class;
        }

        public interface Command {}

        public static class SayHello implements TestHelloActor.Command {
            private final ActorRef<Object> replyTo;

            public SayHello(ActorRef<Object> replyTo) {this.replyTo = replyTo;}
        }

        @Override
        public Behavior<Command> create(String id) {
            return Behaviors.setup(
                    ctx ->
                            Behaviors.receive(Command.class)
                                     .onMessage(SayHello.class, msg -> {
                                         msg.replyTo.tell("hello world!!");
                                         return Behaviors.same();
                                     })
                                     .build());
        }
    }

    @SpringBootApplication(scanBasePackages = "io.github.seonwkim.core")
    static class TestApp {}

    @Nested
    @SpringBootTest(classes = TestApp.class)
    @TestPropertySource(
            properties = {
                    "spring.actor-enabled=true",
                    "spring.actor.pekko.loglevel=INFO",
                    "spring.actor.pekko.actor.provider=local"
            })
    class SimpleTest {

        @Test
        void spawnAndStopActors(ApplicationContext context) {
            assertTrue(context.containsBean("actorSystem"), "ActorSystem bean should exist");
            SpringActorSystem actorSystem = context.getBean(SpringActorSystem.class);

            final String actorId = "test-actor";
            final SpringActorRef<TestHelloActor.Command> actorRef =
                    actorSystem.spawn(TestHelloActor.Command.class, actorId).toCompletableFuture().join();
            assertThat(actorRef).isNotNull();

            assertEquals(actorRef.ask(SayHello::new).toCompletableFuture().join(), "hello world!!");
            assertEquals(actorSystem.stop(TestHelloActor.Command.class, actorId).toCompletableFuture().join().getClass(), Stopped.class);
            assertEquals(actorSystem.stop(TestHelloActor.Command.class, actorId).toCompletableFuture().join().getClass(), ActorNotFound.class);
        }
    }
}

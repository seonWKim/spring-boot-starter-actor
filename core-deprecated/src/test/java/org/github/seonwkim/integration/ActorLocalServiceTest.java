package org.github.seonwkim.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.github.seonwkim.core.PekkoAutoConfiguration;
import org.github.seonwkim.core.service.ActorLocalService;
import org.github.seonwkim.integration.SimpleActorBehavior.AskMessageCommand;
import org.github.seonwkim.integration.SimpleActorBehavior.StopCommand;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(
        properties = {
                "pekko.name=simple",
                "pekko.actor.provider=local",
        },
        locations = "")
@SpringBootTest(classes = { PekkoAutoConfiguration.class })
class ActorLocalServiceTest {

    @Autowired
    private ActorLocalService actorLocalService;

    SimpleActorBehavior behavior;
    ActorRef<SimpleActorBehavior.Command> actorRef;

    @BeforeEach
    void setUp() throws Exception {
        behavior = new SimpleActorBehavior();
        actorRef = actorLocalService.createLocalActor("child-actor-" + UUID.randomUUID(), behavior::create, Duration.ofSeconds(1)).toCompletableFuture().get();
    }

    @AfterEach
    void tearDown() {
        actorLocalService.tell(actorRef, new StopCommand());
    }

    @Test
    void test_tell() throws Exception {
        SimpleActorBehavior.PrintMessageCommand message = new SimpleActorBehavior.PrintMessageCommand(
                "Hello, World!");
        actorLocalService.tell(actorRef, message);
        Thread.sleep(500); // wait for message to be sent
        assertThat(behavior.getCounterForTest()).isEqualTo(1);
    }

    @Test
    void actors_with_same_path_should_not_be_created() throws Exception {
        final String childName = "same-child-actor-name";
        actorLocalService.createLocalActor(childName, new SimpleActorBehavior()::create, Duration.ofSeconds(1))
                         .toCompletableFuture().get();
        assertThrows(Exception.class, () -> actorLocalService.createLocalActor(childName, new SimpleActorBehavior()::create, Duration.ofSeconds(1)).toCompletableFuture().get());
    }

    @Test
    void test_ask() throws Exception {
        CompletableFuture<String> result = actorLocalService.<SimpleActorBehavior.Command, String>ask(
                actorRef,
                replyTo -> new AskMessageCommand<>("Hello, World!", replyTo),
                Duration.ofSeconds(5)
        ).toCompletableFuture();
        String response = result.get();
        assertThat(response).isEqualTo("Received: Hello, World!");
    }
}

class SimpleActorBehavior {

    public int counterForTest = 0;

    public int getCounterForTest() {
        return counterForTest;
    }

    public interface Command {}

    public static class PrintMessageCommand implements Command {
        public final String message;

        public PrintMessageCommand(String message) {
            this.message = message;
        }
    }

    public static class AskMessageCommand<T> implements Command {
        public final String message;
        public final ActorRef<T> replyTo;

        public AskMessageCommand(String message, ActorRef<T> replyTo) {
            this.message = message;
            this.replyTo = replyTo;
        }
    }

    public static class StopCommand implements Command {}

    public String beanName() {
        return "system-simple-actor-behavior";
    }

    public Behavior<Command> create() {
        return Behaviors.setup(
                context ->
                        Behaviors.receive(Command.class)
                                 .onMessage(PrintMessageCommand.class, this::handlePrintMessageCommand)
                                 .onMessage(AskMessageCommand.class, this::handleAskMessageCommand)
                                 .onMessage(StopCommand.class, command -> Behaviors.stopped())
                                 .build());
    }

    private Behavior<Command> handlePrintMessageCommand(PrintMessageCommand command) {
        counterForTest++;
        return Behaviors.same();
    }

    private Behavior<Command> handleAskMessageCommand(AskMessageCommand<String> command) {
        command.replyTo.tell("Received: " + command.message);
        return Behaviors.same();
    }
}

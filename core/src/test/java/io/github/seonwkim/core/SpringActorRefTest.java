package io.github.seonwkim.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import org.apache.pekko.actor.testkit.typed.javadsl.ActorTestKit;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class SpringActorRefTest {

    interface Command {}

    public static class Ping implements Command {
        public final ActorRef<String> replyTo;

        public Ping(ActorRef<String> replyTo) {
            this.replyTo = replyTo;
        }
    }

    public static class SimpleMessage implements Command {
        public final String value;

        public SimpleMessage(String value) {
            this.value = value;
        }
    }

    public static class SlowPing implements Command {
        public final ActorRef<String> replyTo;
        public final Duration delay;

        public SlowPing(ActorRef<String> replyTo, Duration delay) {
            this.replyTo = replyTo;
            this.delay = delay;
        }
    }

    public static Behavior<Command> create(String id, CompletableFuture<String> signal) {
        return Behaviors.receive(Command.class)
                .onMessage(Ping.class, msg -> {
                    msg.replyTo.tell("pong:" + id);
                    return Behaviors.same();
                })
                .onMessage(SimpleMessage.class, msg -> {
                    signal.complete("received: " + msg.value);
                    return Behaviors.same();
                })
                .onMessage(SlowPing.class, msg -> {
                    // Simulate slow response by delaying
                    Behaviors.withTimers(timers -> {
                        timers.startSingleTimer(
                                "delayed-response",
                                new Command() {}, // dummy command
                                msg.delay);
                        return Behaviors.same();
                    });
                    // This will timeout before responding
                    return Behaviors.same();
                })
                .build();
    }

    private ActorTestKit testKit;

    @BeforeEach
    void setup() {
        testKit = ActorTestKit.create(); // JUnit 5-compatible
    }

    @AfterEach
    void tearDown() {
        testKit.shutdownTestKit();
    }

    @Test
    void testAskMethod() throws ExecutionException, InterruptedException {
        String id = UUID.randomUUID().toString();
        Behavior<Command> behavior = create(id, new CompletableFuture<>());

        ActorRef<Command> actorRef = testKit.spawn(behavior, "ask-" + id);
        SpringActorRef<Command> springRef =
                new SpringActorRef<>(testKit.system().scheduler(), actorRef);

        String result = springRef
                .ask(Ping::new, Duration.ofSeconds(3))
                .toCompletableFuture()
                .get();

        assertEquals("pong:" + id, result);
    }

    @Test
    void testTellMethod() throws ExecutionException, InterruptedException {
        String id = UUID.randomUUID().toString();
        CompletableFuture<String> signal = new CompletableFuture<>();
        Behavior<Command> behavior = create(id, signal);

        ActorRef<Command> actorRef = testKit.spawn(behavior, "tell-" + id);
        SpringActorRef<Command> springRef =
                new SpringActorRef<>(testKit.system().scheduler(), actorRef);

        springRef.tell(new SimpleMessage("hello"));
        String result = signal.get();

        assertEquals("received: hello", result);
    }

    @Test
    void testAskBuilderBasic() throws ExecutionException, InterruptedException {
        String id = UUID.randomUUID().toString();
        Behavior<Command> behavior = create(id, new CompletableFuture<>());

        ActorRef<Command> actorRef = testKit.spawn(behavior, "builder-basic-" + id);
        SpringActorRef<Command> springRef =
                new SpringActorRef<>(testKit.system().scheduler(), actorRef);

        String result =
                springRef.askBuilder(Ping::new).execute().toCompletableFuture().get();

        assertEquals("pong:" + id, result);
    }

    @Test
    void testAskBuilderWithCustomTimeout() throws ExecutionException, InterruptedException {
        String id = UUID.randomUUID().toString();
        Behavior<Command> behavior = create(id, new CompletableFuture<>());

        ActorRef<Command> actorRef = testKit.spawn(behavior, "builder-timeout-" + id);
        SpringActorRef<Command> springRef =
                new SpringActorRef<>(testKit.system().scheduler(), actorRef);

        String result = springRef
                .askBuilder(Ping::new)
                .withTimeout(Duration.ofSeconds(5))
                .execute()
                .toCompletableFuture()
                .get();

        assertEquals("pong:" + id, result);
    }

    @Test
    void testAskBuilderOnTimeoutHandler() throws ExecutionException, InterruptedException {
        String id = UUID.randomUUID().toString();
        Behavior<Command> behavior = Behaviors.receive(Command.class)
                .onMessage(Ping.class, msg -> {
                    // Never respond - will timeout
                    return Behaviors.same();
                })
                .build();

        ActorRef<Command> actorRef = testKit.spawn(behavior, "builder-on-timeout-" + id);
        SpringActorRef<Command> springRef =
                new SpringActorRef<>(testKit.system().scheduler(), actorRef);

        String result = springRef
                .askBuilder(Ping::new)
                .withTimeout(Duration.ofMillis(100))
                .onTimeout(() -> "default-value")
                .execute()
                .toCompletableFuture()
                .get();

        assertEquals("default-value", result);
    }

    @Test
    void testAskBuilderOnTimeoutHandlerNotTriggered() throws ExecutionException, InterruptedException {
        String id = UUID.randomUUID().toString();
        Behavior<Command> behavior = create(id, new CompletableFuture<>());

        ActorRef<Command> actorRef = testKit.spawn(behavior, "builder-no-timeout-" + id);
        SpringActorRef<Command> springRef =
                new SpringActorRef<>(testKit.system().scheduler(), actorRef);

        String result = springRef
                .askBuilder(Ping::new)
                .withTimeout(Duration.ofSeconds(3))
                .onTimeout(() -> "default-value")
                .execute()
                .toCompletableFuture()
                .get();

        // Should get actual response, not default value
        assertEquals("pong:" + id, result);
    }

    @Test
    void testAskBuilderWithoutTimeoutHandlerThrows() {
        String id = UUID.randomUUID().toString();
        Behavior<Command> behavior = Behaviors.receive(Command.class)
                .onMessage(Ping.class, msg -> {
                    // Never respond - will timeout
                    return Behaviors.same();
                })
                .build();

        ActorRef<Command> actorRef = testKit.spawn(behavior, "builder-throws-" + id);
        SpringActorRef<Command> springRef =
                new SpringActorRef<>(testKit.system().scheduler(), actorRef);

        ExecutionException exception = assertThrows(ExecutionException.class, () -> {
            springRef
                    .askBuilder(Ping::new)
                    .withTimeout(Duration.ofMillis(100))
                    .execute()
                    .toCompletableFuture()
                    .get();
        });

        // Verify the cause is a TimeoutException
        assertTrue(exception.getCause() instanceof TimeoutException);
    }

    @Test
    void testAskBuilderFluentChaining() throws ExecutionException, InterruptedException {
        String id = UUID.randomUUID().toString();
        Behavior<Command> behavior = Behaviors.receive(Command.class)
                .onMessage(Ping.class, msg -> {
                    // Never respond - will timeout
                    return Behaviors.same();
                })
                .build();

        ActorRef<Command> actorRef = testKit.spawn(behavior, "builder-fluent-" + id);
        SpringActorRef<Command> springRef =
                new SpringActorRef<>(testKit.system().scheduler(), actorRef);

        // Test that all methods can be chained fluently
        String result = springRef
                .askBuilder(Ping::new)
                .withTimeout(Duration.ofMillis(100))
                .onTimeout(() -> "timeout-occurred")
                .execute()
                .toCompletableFuture()
                .get();

        assertEquals("timeout-occurred", result);
    }
}

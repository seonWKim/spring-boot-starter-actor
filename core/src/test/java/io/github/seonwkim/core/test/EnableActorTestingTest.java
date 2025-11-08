package io.github.seonwkim.core.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import io.github.seonwkim.core.EnableActorSupport;
import java.time.Duration;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.context.SpringBootTest;

/** Tests for {@link EnableActorTesting} annotation and basic testing infrastructure. */
@SpringBootTest(classes = EnableActorTestingTest.TestConfig.class)
public class EnableActorTestingTest {

    @SpringBootConfiguration
    @EnableActorSupport
    @EnableActorTesting
    static class TestConfig {}

    @Autowired private SpringActorTestKit testKit;

    interface Command {}

    static class Ping implements Command {
        final ActorRef<String> replyTo;
        final String message;

        Ping(ActorRef<String> replyTo, String message) {
            this.replyTo = replyTo;
            this.message = message;
        }
    }

    static Behavior<Command> createPingPongActor() {
        return Behaviors.receive(Command.class)
                .onMessage(
                        Ping.class,
                        msg -> {
                            msg.replyTo.tell("pong:" + msg.message);
                            return Behaviors.same();
                        })
                .build();
    }

    @Test
    public void testEnableActorTestingInjectsTestKit() {
        assertNotNull(testKit, "SpringActorTestKit should be injected");
        assertNotNull(testKit.getPekkoTestKit(), "Pekko TestKit should be present");
    }

    @Test
    public void testSpawnActor() {
        ActorRef<Command> actor = testKit.spawn(createPingPongActor(), "ping-pong");
        assertNotNull(actor, "Actor should be spawned");
    }

    @Test
    public void testCreateProbe() {
        ActorTestProbe<String> probe = testKit.createProbe();
        assertNotNull(probe, "Probe should be created");
        assertNotNull(probe.ref(), "Probe should have an ActorRef");
    }

    @Test
    public void testProbeExpectMessage() {
        ActorTestProbe<String> probe = testKit.createProbe();
        ActorRef<Command> actor = testKit.spawn(createPingPongActor(), "ping-pong-probe");

        actor.tell(new Ping(probe.ref(), "hello"));

        String response = probe.receiveMessage(Duration.ofSeconds(3));
        assertEquals("pong:hello", response);
    }

    @Test
    public void testProbeExpectMessageClass() {
        ActorTestProbe<String> probe = testKit.createProbe();
        ActorRef<Command> actor = testKit.spawn(createPingPongActor(), "ping-pong-class");

        actor.tell(new Ping(probe.ref(), "world"));

        String response = probe.expectMessageClass(String.class);
        assertEquals("pong:world", response);
    }

    @Test
    public void testProbeExpectMessageThat() {
        ActorTestProbe<String> probe = testKit.createProbe();
        ActorRef<Command> actor = testKit.spawn(createPingPongActor(), "ping-pong-that");

        actor.tell(new Ping(probe.ref(), "test"));

        probe.expectMessageThat(
                String.class,
                response -> {
                    assertEquals("pong:test", response);
                });
    }
}

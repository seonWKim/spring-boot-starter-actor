package io.github.seonwkim.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
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

	public static Behavior<Command> create(String id, CompletableFuture<String> signal) {
		return Behaviors.receive(Command.class)
				.onMessage(
						Ping.class,
						msg -> {
							msg.replyTo.tell("pong:" + id);
							return Behaviors.same();
						})
				.onMessage(
						SimpleMessage.class,
						msg -> {
							signal.complete("received: " + msg.value);
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

		String result = springRef.ask(Ping::new, Duration.ofSeconds(3)).toCompletableFuture().get();

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
}

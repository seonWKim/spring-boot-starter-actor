package io.github.seonwkim.metrics;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.junit.jupiter.api.Test;

class ActorInstrumentationEventListenerTest {

	@Test
	void invokeAdviceTest() {
		AtomicBoolean onEnterCalled = new AtomicBoolean(false);
		AtomicBoolean onExitCalled = new AtomicBoolean(false);

		var listener =
				new ActorInstrumentationEventListener.InvokeAdviceEventListener() {
					@Override
					public void onEnter(Object envelope) {
						onEnterCalled.set(true);
					}

					@Override
					public void onExit(Object envelope, long startTime) {
						onExitCalled.set(true);
					}
				};
		ActorInstrumentationEventListener.register(listener);

		TestActorSystem actorSystem = new TestActorSystem();

		assertDoesNotThrow(
				() -> {
					actorSystem
							.spawn(
									TestHelloActor.class,
									"instrumented-" + UUID.randomUUID(),
									TestHelloActor.create(),
									Duration.ofSeconds(3))
							.toCompletableFuture()
							.get(3, TimeUnit.SECONDS);
				});

		assertTrue(onEnterCalled.get(), "onEnter should have been called");
		assertTrue(onExitCalled.get(), "onExit should have been called");
	}

	@Test
	void invokeAllAdviceTest() {
		AtomicBoolean onEnterCalled = new AtomicBoolean(false);
		AtomicBoolean onExitCalled = new AtomicBoolean(false);

		var listener =
				new ActorInstrumentationEventListener.InvokeAllAdviceEventListener() {
					@Override
					public void onEnter(Object messages) {
						onEnterCalled.set(true);
					}

					@Override
					public void onExit(Object messages, long startTime) {
						onExitCalled.set(true);
					}
				};
		ActorInstrumentationEventListener.register(listener);

		TestActorSystem actorSystem = new TestActorSystem();

		assertDoesNotThrow(
				() -> {
					actorSystem
							.spawn(
									TestHelloActor.Command.class,
									"instrumented-" + UUID.randomUUID(),
									TestHelloActor.create(),
									Duration.ofSeconds(3))
							.toCompletableFuture()
							.get(3, TimeUnit.SECONDS);
				});

		assertTrue(onEnterCalled.get(), "onEnter should have been called");
		assertTrue(onExitCalled.get(), "onExit should have been called");
	}

	static class TestHelloActor {
		public interface Command {}

		public static class SayHello implements TestHelloActor.Command {}

		public static Behavior<Command> create() {
			return Behaviors.setup(
					ctx ->
							Behaviors.receive(TestHelloActor.Command.class)
									.onMessage(TestHelloActor.SayHello.class, msg -> Behaviors.same())
									.build());
		}
	}
}

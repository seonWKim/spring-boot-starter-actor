package io.github.seonwkim.core;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.seonwkim.core.ActorTypeRegistryTest.DummyActor.Command;
import io.github.seonwkim.core.impl.DefaultSpringActorContext;

import java.util.UUID;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class ActorTypeRegistryTest {

	public static class DummyActor implements SpringActor<DummyActor, Command> {

		public interface Command {}

		@Override
		public Behavior<Command> create(SpringActorContext actorContext) {
			return null;
		}
	}

	private ActorTypeRegistry registry;

	@BeforeEach
	public void setUp() {
		registry = new ActorTypeRegistry();
	}

	@Test
	public void testRegisterAndRetrieveByClass() {
		registry.register(
				DummyActor.class,
				(id) ->
						Behaviors.receive(Command.class)
								.onMessage(Command.class, msg -> Behaviors.same())
								.build());

		Behavior<DummyActor.Command> behavior =
                (Behavior<DummyActor.Command>) registry.createBehavior(DummyActor.class, new DefaultSpringActorContext(UUID.randomUUID().toString()));
		assertNotNull(behavior);
	}

	@Test
	public void testRegisterAndRetrieveByStringKey() {
		registry.register(
				DummyActor.class,
				(id) ->
						Behaviors.receive(Command.class)
								.onMessage(Command.class, msg -> Behaviors.same())
								.build());

		Behavior<DummyActor.Command> behavior =
                (Behavior<DummyActor.Command>) registry.createBehavior(DummyActor.class, new DefaultSpringActorContext("custom-id"));

		assertNotNull(behavior);
		assertTrue(behavior instanceof Behavior<?>);
	}

	@Test
	public void testThrowsOnMissingClassKey() {
		assertThrows(
				IllegalArgumentException.class,
				() -> {
					registry.createBehavior(DummyActor.class, new DefaultSpringActorContext("missing"));
				});
	}
}

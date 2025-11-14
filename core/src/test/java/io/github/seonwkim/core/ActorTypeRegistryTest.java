package io.github.seonwkim.core;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.seonwkim.core.ActorTypeRegistryTest.DummyActor.Command;
import io.github.seonwkim.core.impl.DefaultSpringActorContext;
import java.util.UUID;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class ActorTypeRegistryTest {

    public static class DummyActor implements SpringActor<Command> {

        public interface Command {}

        @Override
        public SpringActorBehavior<Command> create(SpringActorContext actorContext) {
            return null;
        }
    }

    @BeforeEach
    public void setUp() {
        // Clear static registry before each test to ensure test isolation
        ActorTypeRegistry.clear();
    }

    @Test
    public void testRegisterAndRetrieveByClass() {
        ActorTypeRegistry.register(DummyActor.class, (id) -> SpringActorBehavior.builder(Command.class, id)
                .onMessage(Command.class, (ctx, msg) -> Behaviors.same())
                .build());

        SpringActorBehavior<DummyActor.Command> behavior = ActorTypeRegistry.createTypedBehavior(
                DummyActor.class,
                new DefaultSpringActorContext(UUID.randomUUID().toString()));
        assertNotNull(behavior);
    }

    @Test
    public void testRegisterAndRetrieveByStringKey() {
        ActorTypeRegistry.register(DummyActor.class, (id) -> SpringActorBehavior.builder(Command.class, id)
                .onMessage(Command.class, (a, b) -> Behaviors.same())
                .build());

        SpringActorBehavior<DummyActor.Command> behavior =
                ActorTypeRegistry.createTypedBehavior(DummyActor.class, new DefaultSpringActorContext("custom-id"));

        assertNotNull(behavior);
    }

    @Test
    public void testThrowsOnMissingClassKey() {
        assertThrows(
                IllegalArgumentException.class,
                () -> ActorTypeRegistry.createTypedBehavior(DummyActor.class, new DefaultSpringActorContext("missing")));
    }
}

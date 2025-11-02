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

    private ActorTypeRegistry registry;

    @BeforeEach
    public void setUp() {
        registry = new ActorTypeRegistry();
    }

    @Test
    public void testRegisterAndRetrieveByClass() {
        registry.register(DummyActor.class, (id) -> SpringActorBehavior.builder(Command.class, id)
                .onMessage(Command.class, (ctx, msg) -> Behaviors.same())
                .build());

        SpringActorBehavior<DummyActor.Command> behavior = registry.createTypedBehavior(
                DummyActor.class,
                new DefaultSpringActorContext(UUID.randomUUID().toString()));
        assertNotNull(behavior);
    }

    @Test
    public void testRegisterAndRetrieveByStringKey() {
        registry.register(DummyActor.class, (id) -> SpringActorBehavior.builder(Command.class, id)
                .onMessage(Command.class, (a, b) -> Behaviors.same())
                .build());

        SpringActorBehavior<DummyActor.Command> behavior =
                registry.createTypedBehavior(DummyActor.class, new DefaultSpringActorContext("custom-id"));

        assertNotNull(behavior);
    }

    @Test
    public void testThrowsOnMissingClassKey() {
        assertThrows(
                IllegalArgumentException.class,
                () -> registry.createTypedBehavior(DummyActor.class, new DefaultSpringActorContext("missing")));
    }
}

package io.github.seonwkim.actor;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.seonwkim.actor.ActorTypeRegistryTest.DummyActor.Command;
import io.github.seonwkim.actor.internal.ActorTypeRegistry;
import io.github.seonwkim.actor.internal.DefaultSpringActorContext;
import java.util.UUID;
import org.junit.jupiter.api.Test;

public class ActorTypeRegistryTest {

    public static class DummyActor implements SpringActor<Command> {

        public interface Command {}

        @Override
        public SpringActorBehavior<Command> create(SpringActorContext actorContext) {
            return null;
        }
    }

    private final ActorTypeRegistry registry = new ActorTypeRegistry();

    @Test
    public void createsTheBehaviorFromTheRegisteredFactory() {
        SpringActorBehavior<Command> registered = SpringActorBehavior.builder(
                        Command.class, new DefaultSpringActorContext("dummy"))
                .onMessage(Command.class, (ctx, msg) -> SpringActorBehavior.same())
                .build();
        registry.register(DummyActor.class, id -> registered);

        assertSame(
                registered,
                registry.createTypedBehavior(
                        DummyActor.class,
                        new DefaultSpringActorContext(UUID.randomUUID().toString())));
    }

    @Test
    public void anUnregisteredClassIsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> registry.createTypedBehavior(DummyActor.class, new DefaultSpringActorContext("missing")));
    }

    @Test
    public void registriesAreIndependent() {
        registry.register(DummyActor.class, id -> SpringActorBehavior.builder(Command.class, id)
                .build());

        assertThrows(IllegalArgumentException.class, () -> new ActorTypeRegistry()
                .createTypedBehavior(DummyActor.class, new DefaultSpringActorContext("other")));
    }
}

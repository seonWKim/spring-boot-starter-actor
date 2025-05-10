package io.github.seonwkim.core;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.github.seonwkim.core.ActorTypeRegistryTest.DummyActor.Command;

public class ActorTypeRegistryTest {

    public static class DummyActor {
        interface Command {}
    }

    private ActorTypeRegistry registry;

    @BeforeEach
    public void setUp() {
        registry = new ActorTypeRegistry();
    }

    @Test
    public void testRegisterAndRetrieveByClass() {
        registry.register(DummyActor.Command.class, (id) -> Behaviors.receive(Command.class)
                                                             .onMessage(Command.class, msg -> Behaviors.same())
                                                             .build());

        Behavior<DummyActor.Command> behavior = registry.createBehavior(DummyActor.Command.class,
                                                                        UUID.randomUUID().toString());
        assertNotNull(behavior);
    }

    @Test
    public void testRegisterAndRetrieveByStringKey() {
        registry.register(DummyActor.Command.class,  (id) -> Behaviors.receive(Command.class)
                                                                      .onMessage(Command.class, msg -> Behaviors.same())
                                                                      .build());

        Behavior<DummyActor.Command> behavior = registry.createBehavior(DummyActor.Command.class, "custom-id");

        assertNotNull(behavior);
        assertTrue(behavior instanceof Behavior<?>);
    }

    @Test
    public void testThrowsOnMissingClassKey() {
        assertThrows(IllegalArgumentException.class, () -> {
            registry.createBehavior(DummyActor.Command.class, "missing");
        });
    }
}

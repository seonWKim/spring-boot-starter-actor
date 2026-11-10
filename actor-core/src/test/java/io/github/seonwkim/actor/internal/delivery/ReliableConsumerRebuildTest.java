package io.github.seonwkim.actor.internal.delivery;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.seonwkim.actor.PostStop;
import io.github.seonwkim.actor.SpringActorBehavior;
import io.github.seonwkim.actor.SpringActorContext;
import io.github.seonwkim.actor.SupervisionStrategy;
import java.lang.reflect.RecordComponent;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.Test;

/**
 * The reliable consumer rebuilds the actor's behavior through the public builder to mark handler
 * success. This fails when {@link SpringActorBehavior.Definition} gains a field the rebuild doesn't
 * carry over, so a new builder option isn't silently dropped for reliable consumers.
 */
class ReliableConsumerRebuildTest {

    interface Command {}

    record Ping() implements Command {}

    @Test
    void rebuildCarriesOverEveryFieldOfTheDefinition() throws Exception {
        SpringActorContext actorContext = new SpringActorContext() {
            @Override
            public String actorId() {
                return "rebuild";
            }
        };
        // Every builder option set, so a field the rebuild drops differs from the original.
        SpringActorBehavior<Command> original = SpringActorBehavior.builder(Command.class, actorContext)
                .withSupervisionStrategy(SupervisionStrategy.restart())
                .withMdc(command -> Map.of("command", "ping"))
                .withState(ctx -> "state")
                .onMessage(Ping.class, (state, ping) -> SpringActorBehavior.same())
                .onSignal(PostStop.class, (state, signal) -> SpringActorBehavior.same())
                .build();

        SpringActorBehavior<Command> rebuilt =
                ReliableConsumerModule.marking(original.definition(), new ReliableConsumerModule.HandlerOutcome());

        for (RecordComponent field : SpringActorBehavior.Definition.class.getRecordComponents()) {
            Object before = field.getAccessor().invoke(original.definition());
            Object after = field.getAccessor().invoke(rebuilt.definition());
            if (field.getName().equals("messageHandlers")) {
                // Wrapped to mark success: same message types in the same order.
                assertThat(handlerTypes(after))
                        .as("message handler types after the rebuild")
                        .isEqualTo(handlerTypes(before));
            } else {
                assertThat(Objects.equals(before, after))
                        .as(
                                "ReliableConsumerModule.marking doesn't carry over Definition.%s (%s became %s)",
                                field.getName(), before, after)
                        .isTrue();
            }
        }
    }

    private static Object handlerTypes(Object handlers) {
        return ((java.util.List<?>) handlers)
                .stream()
                        .map(h -> ((SpringActorBehavior.MessageHandler<?, ?, ?>) h).type())
                        .toList();
    }
}

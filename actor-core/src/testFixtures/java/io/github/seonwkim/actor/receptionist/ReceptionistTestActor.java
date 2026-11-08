package io.github.seonwkim.actor.receptionist;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.seonwkim.actor.SpringActorBehavior;
import io.github.seonwkim.actor.SpringActorContext;
import io.github.seonwkim.actor.SpringActorWithContext;
import io.github.seonwkim.actor.serialization.JsonSerializable;
import java.util.concurrent.BlockingQueue;
import javax.annotation.Nullable;

/**
 * Test actor that records received pings and, when its context carries a service key,
 * registers itself with the Receptionist from behavior setup.
 */
public class ReceptionistTestActor
        implements SpringActorWithContext<ReceptionistTestActor.Command, ReceptionistTestActor.Ctx> {

    /** Messages for the test actor. */
    public interface Command extends JsonSerializable {}

    /** Records its text in the actor's queue. */
    public static class Ping implements Command {
        public final String text;

        @JsonCreator
        public Ping(@JsonProperty("text") String text) {
            this.text = text;
        }
    }

    /** Stops the actor. */
    public static class Stop implements Command {
        @JsonCreator
        public Stop() {}
    }

    /** Context with the received-message queue and an optional key to self-register under. */
    public static class Ctx extends SpringActorContext {
        final String actorId;
        final BlockingQueue<String> received;

        @Nullable final ServiceName<Command> selfRegisterKey;

        public Ctx(String actorId, BlockingQueue<String> received, @Nullable ServiceName<Command> selfRegisterKey) {
            this.actorId = actorId;
            this.received = received;
            this.selfRegisterKey = selfRegisterKey;
        }

        @Override
        public String actorId() {
            return actorId;
        }
    }

    @Override
    public SpringActorBehavior<Command> create(Ctx actorContext) {
        return SpringActorBehavior.builder(Command.class, actorContext)
                .withState(ctx -> {
                    if (actorContext.selfRegisterKey != null) {
                        ctx.registerWithReceptionist(actorContext.selfRegisterKey);
                    }
                    return actorContext;
                })
                .onMessage(Ping.class, (state, msg) -> {
                    state.received.add(msg.text);
                    return SpringActorBehavior.same();
                })
                .onMessage(Stop.class, (state, msg) -> SpringActorBehavior.stopped())
                .build();
    }
}

package io.github.seonwkim.example;

import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import io.github.seonwkim.core.SpringActor;
import org.springframework.stereotype.Component;

@Component
public class HelloActor implements SpringActor {

    public interface Command {}

    public static class SayHello implements Command {
        public final ActorRef<String> replyTo;

        public SayHello(ActorRef<String> replyTo) {
            this.replyTo = replyTo;
        }
    }

    @Override
    public Class<?> commandClass() {
        return Command.class;
    }

    public static Behavior<Command> create(String id) {
        return Behaviors.setup(ctx -> new HelloActorBehavior(ctx, id).create());
    }

    // Inner class to isolate stateful behavior logic
    private static class HelloActorBehavior {
        private final ActorContext<Command> ctx;
        private final String actorId;

        HelloActorBehavior(ActorContext<Command> ctx, String actorId) {
            this.ctx = ctx;
            this.actorId = actorId;
        }

        public Behavior<Command> create() {
            return Behaviors.receive(Command.class)
                            .onMessage(SayHello.class, this::onSayHello)
                            .build();
        }

        private Behavior<Command> onSayHello(SayHello msg) {
            ctx.getLog().info("Received SayHello for id={}", actorId);
            msg.replyTo.tell("Hello from actor " + actorId);
            return Behaviors.same();
        }
    }
}

package org.github.seonwkim.core.fixture;

import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.github.seonwkim.core.SpringActor;

@SpringActor
public class TestHelloActor {

    public interface Command {}

    public static class SayHello implements Command {}

    public static Behavior<Command> create(String id) {
        return Behaviors.setup(ctx ->
            Behaviors.receive(Command.class)
                .onMessage(SayHello.class, msg -> {
                    System.out.println("Hello from " + id);
                    return Behaviors.same();
                })
                .build()
        );
    }
}

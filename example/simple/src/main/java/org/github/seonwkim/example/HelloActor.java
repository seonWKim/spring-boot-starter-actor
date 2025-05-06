package org.github.seonwkim.example;

import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.github.seonwkim.core.SpringActor;
import org.github.seonwkim.example.HelloActor.Command;
import org.springframework.stereotype.Component;

@Component
@SpringActor(commandClass = Command.class)
public class HelloActor {

    public interface Command {}

    public static class SayHello implements Command {}

    public static Behavior<Command> create(String id) {
        return Behaviors.setup(ctx ->
                                       Behaviors.receive(Command.class)
                                                .onMessage(SayHello.class, msg -> Behaviors.same())
                                                .build());
    }
}

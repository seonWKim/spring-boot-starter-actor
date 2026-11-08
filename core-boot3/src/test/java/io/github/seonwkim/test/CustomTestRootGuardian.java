package io.github.seonwkim.test;

import io.github.seonwkim.core.RootGuardian;
import io.github.seonwkim.core.RootGuardian.Command;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.Behaviors;

public class CustomTestRootGuardian {
    public static Behavior<Command> create() {
        return Behaviors.setup(
                ctx -> Behaviors.receive(RootGuardian.Command.class).build());
    }
}

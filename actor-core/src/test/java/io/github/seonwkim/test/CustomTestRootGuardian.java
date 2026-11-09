package io.github.seonwkim.test;

import io.github.seonwkim.actor.internal.RootGuardian;
import io.github.seonwkim.actor.internal.RootGuardian.Command;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.Behaviors;

public class CustomTestRootGuardian {
    public static Behavior<Command> create() {
        return Behaviors.setup(
                ctx -> Behaviors.receive(RootGuardian.Command.class).build());
    }
}

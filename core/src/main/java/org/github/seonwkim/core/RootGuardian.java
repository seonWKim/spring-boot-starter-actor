package org.github.seonwkim.core;

import org.apache.pekko.actor.typed.Behavior;
import org.github.seonwkim.core.impl.DefaultRootGuardian;

public interface RootGuardian {
    /**
     * Base command type for RootGuardian-compatible actors.
     */
    interface Command {}

    /**
     * Create the RootGuardian behavior using the given actor type registry.
     */
    static Behavior<Command> create(ActorTypeRegistry registry) {
        return DefaultRootGuardian.create(registry);
    }
}

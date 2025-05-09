package io.github.seonwkim.core;

import org.apache.pekko.actor.typed.Behavior;
import io.github.seonwkim.core.impl.DefaultRootGuardian;

/**
 * Root guardian interface for the actor system.
 * The root guardian is the top-level actor that manages the lifecycle of all other actors.
 * It handles commands for spawning actors and maintains references to them.
 */
public interface RootGuardian {
    /**
     * Base command type for RootGuardian-compatible actors.
     * All commands sent to the RootGuardian must implement this interface.
     */
    interface Command {}

    /**
     * Creates the default RootGuardian behavior using the given actor type registry.
     *
     * @param registry The ActorTypeRegistry to use for creating actor behaviors
     * @return A behavior for the RootGuardian
     */
    static Behavior<Command> create(ActorTypeRegistry registry) {
        return DefaultRootGuardian.create(registry);
    }
}

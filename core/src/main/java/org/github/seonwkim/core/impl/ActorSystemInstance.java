package org.github.seonwkim.core.impl;

import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.actor.typed.Behavior;
import org.github.seonwkim.core.RootGuardian;

public class ActorSystemInstance {

    private final ActorSystem<RootGuardian.Command> actorSystem;

    public ActorSystemInstance(ActorSystem<RootGuardian.Command> actorSystem) {
        this.actorSystem = actorSystem;
    }

    public ActorSystem<RootGuardian.Command> getRaw() {
        return actorSystem;
    }

    public void terminate() {
        actorSystem.terminate();
    }

    public <T> ActorRef<T> spawn(Behavior<T> behavior, String name) {
        return null;
    }
}

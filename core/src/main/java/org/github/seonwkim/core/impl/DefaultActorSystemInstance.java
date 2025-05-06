package org.github.seonwkim.core.impl;

import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.actor.typed.Behavior;
import org.github.seonwkim.core.ActorSystemInstance;
import org.github.seonwkim.core.RootGuardian;

public class DefaultActorSystemInstance implements ActorSystemInstance {

    private final ActorSystem<RootGuardian.Command> actorSystem;

    public DefaultActorSystemInstance(ActorSystem<RootGuardian.Command> actorSystem) {
        this.actorSystem = actorSystem;
    }

    @Override
    public ActorSystem<RootGuardian.Command> getRaw() {
        return actorSystem;
    }

    @Override
    public void terminate() {
        actorSystem.terminate();
    }

    @Override
    public <T> ActorRef<T> spawn(Behavior<T> behavior, String name) {
        return null;
    }
}

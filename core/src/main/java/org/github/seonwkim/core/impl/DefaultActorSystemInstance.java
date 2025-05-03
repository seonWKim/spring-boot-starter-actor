package org.github.seonwkim.core.impl;

import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.actor.typed.Behavior;
import org.github.seonwkim.core.ActorSystemInstance;

public class DefaultActorSystemInstance implements ActorSystemInstance {

    private final ActorSystem<Void> actorSystem;

    public DefaultActorSystemInstance(ActorSystem<Void> actorSystem) {
        this.actorSystem = actorSystem;
    }

    @Override
    public ActorSystem<Void> getRaw() {
        return actorSystem;
    }

    @Override
    public void terminate() {
        actorSystem.terminate();
    }

    @Override
    public <T> ActorRef<T> spawn(Behavior<T> behavior, String name) {
        // TODO
        return null;
    }
}

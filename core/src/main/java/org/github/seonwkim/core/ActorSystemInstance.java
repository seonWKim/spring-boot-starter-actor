package org.github.seonwkim.core;

import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.actor.typed.Behavior;

public interface ActorSystemInstance {
    ActorSystem<Void> getRaw();

    void terminate();

    <T> ActorRef<T> spawn(Behavior<T> behavior, String name);
}

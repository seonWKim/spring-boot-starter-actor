package org.github.seonwkim.core.config;

import java.util.concurrent.CompletionStage;

import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.Terminated;

public interface ActorSystemInstance {
    ActorSystem<Void> getRaw();

    void terminate();

    <T> ActorRef<T> spawn(Behavior<T> behavior, String name);
}

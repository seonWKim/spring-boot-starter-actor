package org.github.seonwkim.core;

import java.time.Duration;
import java.util.concurrent.CompletionStage;

import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.actor.typed.javadsl.AskPattern;
import org.github.seonwkim.core.impl.DefaultRootGuardian;

public class ActorSystemInstance {

    private final ActorSystem<RootGuardian.Command> actorSystem;
    private static final Duration TIMEOUT = Duration.ofSeconds(3); // configurable if needed

    public ActorSystemInstance(ActorSystem<RootGuardian.Command> actorSystem) {
        this.actorSystem = actorSystem;
    }

    public ActorSystem<RootGuardian.Command> getRaw() {
        return actorSystem;
    }

    public void terminate() {
        actorSystem.terminate();
    }

    public <T> CompletionStage<ActorRef<T>> spawn(Class<T> commandClass, String actorId) {
        return AskPattern.ask(
                actorSystem,
                (ActorRef<DefaultRootGuardian.Spawned<T>> replyTo) ->
                        new DefaultRootGuardian.SpawnActor<>(commandClass, actorId, replyTo),
                TIMEOUT,
                actorSystem.scheduler()
        ).thenApply(spawned -> spawned.ref);
    }

    public <T> CompletionStage<ActorRef<T>> spawn(Class<T> commandClass, String actorId, Duration timeout) {
        return AskPattern.ask(
                actorSystem,
                (ActorRef<DefaultRootGuardian.Spawned<T>> replyTo) ->
                        new DefaultRootGuardian.SpawnActor<>(commandClass, actorId, replyTo),
                timeout,
                actorSystem.scheduler()
        ).thenApply(spawned -> spawned.ref);
    }
}

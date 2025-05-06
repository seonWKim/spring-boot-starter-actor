package org.github.seonwkim.core;

import java.time.Duration;
import java.util.concurrent.CompletionStage;

import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.actor.typed.javadsl.AskPattern;
import org.github.seonwkim.core.impl.DefaultRootGuardian;

public class SpringActorSystem {

    private final ActorSystem<RootGuardian.Command> actorSystem;
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(3); // configurable if needed

    public SpringActorSystem(ActorSystem<RootGuardian.Command> actorSystem) {
        this.actorSystem = actorSystem;
    }

    public ActorSystem<RootGuardian.Command> getRaw() {
        return actorSystem;
    }

    public void terminate() {
        actorSystem.terminate();
    }

    public <T> CompletionStage<SpringActorRef<T>> spawn(Class<T> commandClass, String actorId) {
        return AskPattern.ask(
                actorSystem,
                (ActorRef<DefaultRootGuardian.Spawned<T>> replyTo) ->
                        new DefaultRootGuardian.SpawnActor<>(commandClass, actorId, replyTo),
                DEFAULT_TIMEOUT,
                actorSystem.scheduler()
        ).thenApply(spawned -> new SpringActorRef<>(actorSystem.scheduler(), spawned.ref));
    }

    public <T> CompletionStage<SpringActorRef<T>> spawn(
            Class<T> commandClass,
            String actorId,
            Duration timeout) {
        return AskPattern.ask(
                actorSystem,
                (ActorRef<DefaultRootGuardian.Spawned<T>> replyTo) ->
                        new DefaultRootGuardian.SpawnActor<>(commandClass, actorId, replyTo),
                timeout,
                actorSystem.scheduler()
        ).thenApply(spawned -> new SpringActorRef<>(actorSystem.scheduler(), spawned.ref));
    }
}

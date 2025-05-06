package org.github.seonwkim.core;

import java.time.Duration;
import java.util.concurrent.CompletionStage;

import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.actor.typed.javadsl.AskPattern;
import org.apache.pekko.cluster.typed.Cluster;
import org.github.seonwkim.core.impl.DefaultRootGuardian;
import org.springframework.lang.Nullable;

public class SpringActorSystem {

    private final ActorSystem<RootGuardian.Command> actorSystem;
    private final boolean isClusterMode;
    @Nullable
    private final Cluster cluster;
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(3); // configurable if needed

    public SpringActorSystem(ActorSystem<RootGuardian.Command> actorSystem, boolean isClusterMode) {
        this.actorSystem = actorSystem;
        this.isClusterMode = isClusterMode;
        this.cluster = isClusterMode ? Cluster.get(actorSystem) : null;
    }

    public ActorSystem<RootGuardian.Command> getRaw() {
        return actorSystem;
    }

    public boolean isClusterMode() {
        return isClusterMode;
    }

    @Nullable
    public Cluster getCluster() {
        return cluster;
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

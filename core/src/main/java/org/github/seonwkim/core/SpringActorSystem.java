package org.github.seonwkim.core;

import java.time.Duration;
import java.util.concurrent.CompletionStage;

import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.actor.typed.Props;
import org.apache.pekko.actor.typed.javadsl.AskPattern;
import org.apache.pekko.cluster.ClusterEvent;
import org.apache.pekko.cluster.typed.Cluster;
import org.apache.pekko.cluster.typed.Subscribe;
import org.github.seonwkim.core.behavior.ClusterEventBehavior;
import org.github.seonwkim.core.impl.DefaultRootGuardian;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.lang.Nullable;

public class SpringActorSystem implements DisposableBean {

    private final ActorSystem<RootGuardian.Command> actorSystem;
    @Nullable
    private final Cluster cluster;
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(3); // configurable if needed

    public SpringActorSystem(ActorSystem<RootGuardian.Command> actorSystem) {
        this.actorSystem = actorSystem;
        this.cluster = null;
    }

    public SpringActorSystem(
            ActorSystem<RootGuardian.Command> actorSystem,
            Cluster cluster,
            ApplicationEventPublisher publisher
    ) {
        this.actorSystem = actorSystem;
        this.cluster = cluster;
        ActorRef<ClusterEvent.ClusterDomainEvent> listener = actorSystem.systemActorOf(
                ClusterEventBehavior.create(publisher), "cluster-event-listener", Props.empty());

        cluster.subscriptions().tell(new Subscribe<>(listener, ClusterEvent.ClusterDomainEvent.class));
    }

    public ActorSystem<RootGuardian.Command> getRaw() {
        return actorSystem;
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

    @Override
    public void destroy() throws Exception {
        actorSystem.terminate();
        actorSystem.getWhenTerminated().toCompletableFuture().join();
    }
}

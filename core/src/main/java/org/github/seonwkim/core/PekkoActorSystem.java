package org.github.seonwkim.core;

import org.apache.pekko.actor.typed.ActorSystem;

public class PekkoActorSystem {

    private final ActorSystem<?> actorSystem;
    private final boolean clusterConfigured;

    public PekkoActorSystem(ActorSystem<?> actorSystem, boolean clusterConfigured) {
        this.actorSystem = actorSystem;
        this.clusterConfigured = clusterConfigured;
    }

    public ActorSystem<?> getActorSystem() {
        return actorSystem;
    }

    public boolean isClusterConfigured() {
        return clusterConfigured;
    }
}

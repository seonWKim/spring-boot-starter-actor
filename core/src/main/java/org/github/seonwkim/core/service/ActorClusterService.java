package org.github.seonwkim.core.service;

import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.cluster.ClusterEvent.CurrentClusterState;
import org.apache.pekko.cluster.sharding.typed.javadsl.EntityRef;
import org.apache.pekko.cluster.sharding.typed.javadsl.EntityTypeKey;
import org.github.seonwkim.core.PekkoCluster;
import org.github.seonwkim.core.PekkoClusterSharding;
import org.springframework.stereotype.Component;

@Component
public class ActorClusterService {

    private final ActorSystem<?> actorSystem;
    private final PekkoCluster cluster;
    private final PekkoClusterSharding clusterSharding;

    private final boolean clusterConfigured;

    public ActorClusterService(
            ActorSystem<?> actorSystem,
            PekkoCluster pekkoCluster,
            PekkoClusterSharding clusterSharding) {
        this.actorSystem = actorSystem;
        this.cluster = pekkoCluster;
        this.clusterSharding = clusterSharding;
        this.clusterConfigured = pekkoCluster.isClusterConfigured();
    }

    public <T> EntityRef<T> getShardedActor(EntityTypeKey<T> entityTypeKey, String entityId) {
        return clusterSharding.getClusterSharding().entityRefFor(entityTypeKey, entityId);
    }

    public CurrentClusterState getClusterState() {
        if (!clusterConfigured) throw new RuntimeException("Cluster isn't configured");
        return cluster.state();
    }
}

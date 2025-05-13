package io.github.seonwkim.example;

import java.util.Map;

import org.apache.pekko.cluster.typed.Cluster;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.stereotype.Component;

import io.github.seonwkim.core.MetricsUtils;
import io.github.seonwkim.core.SpringActorSystem;

@Component
@Endpoint(id = "pekko-cluster")
public class PekkoClusterEndpoint {

    private final SpringActorSystem springActorSystem;

    public PekkoClusterEndpoint(SpringActorSystem springActorSystem) {
        this.springActorSystem = springActorSystem;
    }

    @ReadOperation
    public Map<String, Object> clusterState() {
        final Cluster cluster = springActorSystem.getCluster();
        return MetricsUtils.getClusterState(springActorSystem);
    }
}

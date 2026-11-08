package io.github.seonwkim.example;

import io.github.seonwkim.actor.SpringActorSystem;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.pekko.cluster.typed.Cluster;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.stereotype.Component;

@Component
@Endpoint(id = "actor-cluster-state")
public class ActorClusterStateEndpoint {

    private final SpringActorSystem springActorSystem;

    public ActorClusterStateEndpoint(SpringActorSystem springActorSystem) {
        this.springActorSystem = springActorSystem;
    }

    @ReadOperation
    public Map<String, Object> getClusterState() {
        Cluster cluster = springActorSystem.unwrap(Cluster.class);
        if (cluster == null) {
            return Map.of();
        }

        List<Map<String, Object>> members = new ArrayList<>();
        cluster.state().getMembers().forEach(member -> {
            Map<String, Object> entry = new HashMap<>();
            entry.put("address", member.address().toString());
            entry.put("status", member.status().toString());
            entry.put("roles", new ArrayList<>(member.getRoles()));
            members.add(entry);
        });

        Map<String, Object> result = new HashMap<>();
        result.put("selfAddress", cluster.selfMember().address().toString());
        result.put("selfStatus", cluster.selfMember().status().toString());
        result.put("members", members);
        return result;
    }
}

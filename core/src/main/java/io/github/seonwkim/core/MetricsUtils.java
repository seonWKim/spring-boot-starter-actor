package io.github.seonwkim.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.apache.pekko.cluster.typed.Cluster;

/**
 * Utility class for retrieving metrics and state information from the Apache Pekko cluster.
 */
public class MetricsUtils {

    /**
     * Retrieves the current cluster state as a structured map, including:
     * <ul>
     *   <li>The address and status of the local (self) node</li>
     *   <li>A list of all members in the cluster, each with their address, status, and roles</li>
     * </ul>
     *
     * This data can be used for monitoring, actuator endpoints, or custom dashboards.
     *
     * @param springActorSystem the Spring-integrated actor system wrapper providing access to the Pekko cluster
     * @return a map representing the cluster state; returns an empty map if the cluster is not available
     */
    public static Map<String, Object> getClusterState(SpringActorSystem springActorSystem) {
        final Cluster cluster = springActorSystem.getCluster();
        if (cluster == null) {
            return Collections.emptyMap();
        }

        ArrayList<Map<String, Object>> members = new ArrayList<>();
        cluster.state().getMembers()
               .forEach(member -> {
                   Map<String, Object> temp = new HashMap<>();
                   temp.put("address", member.address().toString());
                   temp.put("status", member.status().toString());
                   temp.put("roles", new ArrayList<>(member.getRoles()));
                   members.add(temp);
               });

        Map<String, Object> result = new HashMap<>();
        result.put("selfAddress", cluster.selfMember().address().toString());
        result.put("selfStatus", cluster.selfMember().status().toString());
        result.put("members", members);

        return result;
    }
}

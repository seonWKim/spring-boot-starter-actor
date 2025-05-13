package io.github.seonwkim.core.utils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.pekko.cluster.Member;
import org.apache.pekko.cluster.MemberStatus;
import org.apache.pekko.cluster.typed.Cluster;

import io.github.seonwkim.core.SpringActorSystem;

/**
 * Utility class for retrieving metrics and state information from the Apache Pekko cluster.
 */
public class MetricsUtils {

    /**
     * Retrieves the current cluster state as a structured map, including:
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

    /**
     * Extracts aggregate metrics from the cluster such as member counts, leader info, and unreachable members.
     */
    public static Map<String, Object> getClusterMetrics(SpringActorSystem springActorSystem) {
        final Cluster cluster = springActorSystem.getCluster();
        if (cluster == null) {
            return Collections.emptyMap();
        }

        List<Member> members = IteratorUtils.fromIterable(cluster.state().getMembers());
        long upCount = members.stream().filter(m -> m.status().equals(MemberStatus.up())).count();
        long unreachableCount = cluster.state().getUnreachable().size();
        Set<String> roles = cluster.selfMember().getRoles();

        Map<String, Object> metrics = new HashMap<>();
        if (cluster.state().getLeader() != null) {
            metrics.put("leader", cluster.state().getLeader().toString());
        }
        metrics.put("memberCount", members.size());
        metrics.put("upCount", upCount);
        metrics.put("unreachableCount", unreachableCount);
        metrics.put("selfRoles", new ArrayList<>(roles));

        return metrics;
    }
}

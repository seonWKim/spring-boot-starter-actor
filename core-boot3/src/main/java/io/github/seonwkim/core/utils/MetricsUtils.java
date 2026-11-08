package io.github.seonwkim.core.utils;

import io.github.seonwkim.core.SpringActorSystem;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.pekko.cluster.Member;
import org.apache.pekko.cluster.MemberStatus;
import org.apache.pekko.cluster.typed.Cluster;

/** Utility class for retrieving metrics and state information from the Apache Pekko cluster. */
public class MetricsUtils {

    /**
     * Retrieves the current cluster state as a structured map, including member addresses, statuses,
     * roles, and self-node information.
     *
     * @param springActorSystem The SpringActorSystem to get cluster state from
     * @return A map containing cluster state information, or an empty map if not in cluster mode
     */
    public static Map<String, Object> getClusterState(SpringActorSystem springActorSystem) {
        final Cluster cluster = springActorSystem.getCluster();
        if (cluster == null) {
            return Collections.emptyMap();
        }

        ArrayList<Map<String, Object>> members = new ArrayList<>();
        cluster.state().getMembers().forEach(member -> {
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
     * Extracts aggregate metrics from the cluster such as member counts, leader info, and unreachable
     * members.
     */
    public static Map<String, Object> getClusterMetrics(SpringActorSystem springActorSystem) {
        final Cluster cluster = springActorSystem.getCluster();
        if (cluster == null) {
            return Collections.emptyMap();
        }

        List<Member> members = IteratorUtils.fromIterable(cluster.state().getMembers());
        long upCount = members.stream()
                .filter(m -> m.status().equals(MemberStatus.up()))
                .count();
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

    /**
     * Retrieves a list of all members in the cluster.
     *
     * @param springActorSystem The SpringActorSystem to get cluster members from
     * @return A list of all members in the cluster, or an empty list if not in cluster mode
     */
    public static List<Member> getMembers(SpringActorSystem springActorSystem) {
        final Cluster cluster = springActorSystem.getCluster();
        if (cluster == null) {
            return Collections.emptyList();
        }

        return IteratorUtils.fromIterable(cluster.state().getMembers());
    }

    /**
     * Returns the total number of members in the cluster.
     *
     * @param springActorSystem The SpringActorSystem to get the member count from
     * @return The number of members in the cluster, or 0 if not in cluster mode
     */
    public static long getMemberCount(SpringActorSystem springActorSystem) {
        return getMembers(springActorSystem).size();
    }

    public static long getUpCount(SpringActorSystem springActorSystem) {
        return getMembers(springActorSystem).stream()
                .filter(m -> m.status().equals(MemberStatus.up()))
                .count();
    }

    public static long getUnreachableCount(SpringActorSystem springActorSystem) {
        final Cluster cluster = springActorSystem.getCluster();
        if (cluster == null) {
            return 0;
        }
        return cluster.state().getUnreachable().size();
    }

    public static Set<String> getSelfRoles(SpringActorSystem springActorSystem) {
        final Cluster cluster = springActorSystem.getCluster();
        if (cluster == null) {
            return Collections.emptySet();
        }
        return cluster.selfMember().getRoles();
    }
}

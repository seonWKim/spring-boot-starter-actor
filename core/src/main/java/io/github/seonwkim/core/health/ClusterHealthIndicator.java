package io.github.seonwkim.core.health;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.pekko.cluster.Cluster;
import org.apache.pekko.cluster.ClusterEvent;
import org.apache.pekko.cluster.Member;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import io.github.seonwkim.core.SpringActorSystem;

/**
 * Health indicator for cluster state and split brain risk detection.
 *
 * <p>This health indicator reports the health of the actor cluster based on:
 * <ul>
 *   <li>Number of reachable vs unreachable members</li>
 *   <li>Split brain risk level based on unreachable member count</li>
 *   <li>Cluster leadership status</li>
 * </ul>
 *
 * <p>Health status levels:
 * <ul>
 *   <li><b>UP</b>: All cluster members are reachable</li>
 *   <li><b>DEGRADED</b>: Some members unreachable but no split brain risk</li>
 *   <li><b>DOWN</b>: High split brain risk (50%+ members unreachable)</li>
 * </ul>
 *
 * <p>Enable this health indicator with:
 * <pre>
 * management:
 *   health:
 *     cluster:
 *       enabled: true
 * </pre>
 */
@Component
@ConditionalOnClass(name = "org.springframework.boot.actuate.health.HealthIndicator")
@ConditionalOnProperty(
        prefix = "management.health.cluster",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
public class ClusterHealthIndicator implements HealthIndicator {

    private final SpringActorSystem actorSystem;

    public ClusterHealthIndicator(SpringActorSystem actorSystem) {
        this.actorSystem = actorSystem;
    }

    @Override
    public Health health() {
        try {
            Cluster cluster = Cluster.get(actorSystem.getRaw());
            ClusterEvent.CurrentClusterState state = cluster.state();

            // Get cluster members (convert Iterable to Set)
            Set<Member> members = new java.util.HashSet<>();
            state.getMembers().forEach(members::add);
            
            Set<Member> unreachable = new java.util.HashSet<>();
            state.getUnreachable().forEach(unreachable::add);
            
            int reachableCount = members.size() - unreachable.size();
            int unreachableCount = unreachable.size();

            // Determine health status based on unreachable members
            Health.Builder builder;
            String splitBrainRisk;

            if (unreachableCount == 0) {
                // All nodes reachable - healthy
                builder = Health.up();
                splitBrainRisk = "LOW";
            } else if (unreachableCount >= members.size() / 2) {
                // Potential split brain - critical
                builder = Health.down();
                splitBrainRisk = "CRITICAL";
            } else if (unreachableCount >= members.size() / 3) {
                // Some unreachable - degraded
                builder = Health.status("DEGRADED");
                splitBrainRisk = "HIGH";
            } else {
                // Minor issues - degraded
                builder = Health.status("DEGRADED");
                splitBrainRisk = "MEDIUM";
            }

            // Add cluster state details
            return builder
                    .withDetail("clusterSize", members.size())
                    .withDetail("reachableNodes", reachableCount)
                    .withDetail("unreachableNodes", unreachableCount)
                    .withDetail("unreachableMembers", formatMembers(unreachable))
                    .withDetail("splitBrainRisk", splitBrainRisk)
                    .withDetail("selfAddress", cluster.selfMember().address().toString())
                    .withDetail("selfStatus", cluster.selfMember().status().toString())
                    .build();

        } catch (Exception e) {
            // If cluster is not available or error occurs
            return Health.down()
                    .withDetail("error", e.getMessage())
                    .withDetail("splitBrainRisk", "UNKNOWN")
                    .build();
        }
    }

    /**
     * Formats a set of members as a list of address strings.
     */
    private List<String> formatMembers(Set<Member> members) {
        return members.stream()
                .map(m -> m.address().toString() + " (status: " + m.status() + ")")
                .collect(Collectors.toList());
    }
}

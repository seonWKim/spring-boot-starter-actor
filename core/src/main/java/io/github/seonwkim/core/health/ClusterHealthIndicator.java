package io.github.seonwkim.core.health;

import org.apache.pekko.cluster.ClusterEvent.CurrentClusterState;
import org.apache.pekko.cluster.Member;
import org.apache.pekko.cluster.MemberStatus;
import org.apache.pekko.cluster.typed.Cluster;
import org.springframework.boot.actuate.health.AbstractHealthIndicator;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

/**
 * Reports this node's cluster membership.
 *
 * <ul>
 *   <li>{@code UP} while this member is {@code Up}.
 *   <li>{@code DOWN} when this member is unreachable, {@code Down} or {@code Removed}, or the cluster
 *       extension has terminated.
 *   <li>{@code OUT_OF_SERVICE} in every other state: joining, weakly up, leaving, exiting or
 *       preparing for shutdown.
 * </ul>
 *
 * <p>Details hold only counts and this node's status ({@code members}, {@code unreachable},
 * {@code status}); never member addresses. Registered as the {@code actorCluster} health
 * contributor by {@link ActorHealthAutoConfiguration}.
 */
public class ClusterHealthIndicator extends AbstractHealthIndicator {

    private final Cluster cluster;

    public ClusterHealthIndicator(Cluster cluster) {
        super("Actor cluster health check failed");
        this.cluster = cluster;
    }

    @Override
    protected void doHealthCheck(Health.Builder builder) {
        Member self = cluster.selfMember();
        CurrentClusterState state = cluster.state();
        int members = 0;
        for (Member ignored : state.getMembers()) {
            members++;
        }
        boolean selfLost = cluster.isTerminated() || state.getUnreachable().contains(self);
        builder.status(statusOf(self.status(), selfLost))
                .withDetail("members", members)
                .withDetail("unreachable", state.getUnreachable().size())
                .withDetail("status", self.status().toString());
    }

    /**
     * Maps this node's member status to a health status. {@code selfLost} is true when this node is
     * unreachable or its cluster extension has terminated.
     */
    static Status statusOf(MemberStatus status, boolean selfLost) {
        if (selfLost
                || MemberStatus.down().equals(status)
                || MemberStatus.removed().equals(status)) {
            return Status.DOWN;
        }
        if (MemberStatus.up().equals(status)) {
            return Status.UP;
        }
        return Status.OUT_OF_SERVICE;
    }
}

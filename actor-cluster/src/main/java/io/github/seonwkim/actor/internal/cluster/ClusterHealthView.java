package io.github.seonwkim.actor.internal.cluster;

import io.github.seonwkim.actor.SpringActorSystem;
import io.github.seonwkim.actor.cluster.MemberStatus;
import java.util.stream.StreamSupport;
import javax.annotation.Nullable;
import org.apache.pekko.cluster.ClusterEvent.CurrentClusterState;
import org.apache.pekko.cluster.Member;
import org.apache.pekko.cluster.typed.Cluster;

/**
 * This node's view of the cluster, read for health checks.
 *
 * @param selfStatus this node's status, or null while it has not joined yet
 * @param selfUnreachable whether other members report this node as unreachable
 * @param terminated whether this node's cluster extension has shut down
 * @param members the members this node sees
 * @param unreachable how many of them are unreachable
 */
public record ClusterHealthView(
        @Nullable MemberStatus selfStatus, boolean selfUnreachable, boolean terminated, int members, int unreachable) {

    public static ClusterHealthView of(SpringActorSystem system) {
        Cluster cluster = system.unwrap(Cluster.class);
        CurrentClusterState state = cluster.state();
        Member self = cluster.selfMember();
        boolean joined = StreamSupport.stream(state.getMembers().spliterator(), false)
                .anyMatch(member -> member.uniqueAddress().equals(self.uniqueAddress()));
        return new ClusterHealthView(
                joined ? PekkoClusterEvents.status(self.status()) : null,
                state.getUnreachable().contains(self),
                cluster.isTerminated(),
                state.members().size(),
                state.getUnreachable().size());
    }
}

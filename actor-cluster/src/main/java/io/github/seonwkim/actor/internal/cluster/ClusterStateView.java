package io.github.seonwkim.actor.internal.cluster;

import io.github.seonwkim.actor.SpringActorSystem;
import io.github.seonwkim.actor.cluster.MemberStatus;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import javax.annotation.Nullable;
import org.apache.pekko.cluster.ClusterEvent.CurrentClusterState;
import org.apache.pekko.cluster.Member;
import org.apache.pekko.cluster.typed.Cluster;

/**
 * This node's view of the cluster, read for health checks, metrics and the {@code actors} endpoint.
 *
 * @param selfStatus this node's status, or null while it has not joined yet
 * @param selfUnreachable whether other members report this node as unreachable
 * @param terminated whether this node's cluster extension has shut down
 * @param members the members this node sees
 * @param unreachable how many of them are unreachable
 * @param leader whether this node is the cluster leader
 * @param singletonHost whether this node runs the cluster singletons: it is the oldest member that is up
 * @param nodes each member this node sees, oldest first
 */
public record ClusterStateView(
        @Nullable MemberStatus selfStatus,
        boolean selfUnreachable,
        boolean terminated,
        int members,
        int unreachable,
        boolean leader,
        boolean singletonHost,
        List<Node> nodes) {

    /**
     * One member.
     *
     * @param address its node address
     * @param status its membership status
     * @param roles its cluster roles
     * @param reachable whether this node can reach it
     */
    public record Node(String address, MemberStatus status, Set<String> roles, boolean reachable) {}

    public static ClusterStateView of(SpringActorSystem system) {
        Cluster cluster = system.unwrap(Cluster.class);
        CurrentClusterState state = cluster.state();
        Member self = cluster.selfMember();
        List<Member> members = StreamSupport.stream(state.getMembers().spliterator(), false)
                .sorted(Member.ageOrdering()::compare)
                .toList();
        boolean joined =
                members.stream().anyMatch(member -> member.uniqueAddress().equals(self.uniqueAddress()));
        Member oldestUp = members.stream()
                .filter(member -> member.status() == org.apache.pekko.cluster.MemberStatus.up())
                .findFirst()
                .orElse(null);
        return new ClusterStateView(
                joined ? PekkoClusterEvents.status(self.status()) : null,
                state.getUnreachable().contains(self),
                cluster.isTerminated(),
                members.size(),
                state.getUnreachable().size(),
                self.address().equals(state.getLeader()),
                oldestUp != null && oldestUp.uniqueAddress().equals(self.uniqueAddress()),
                members.stream()
                        .map(member -> new Node(
                                member.address().toString(),
                                PekkoClusterEvents.status(member.status()),
                                member.getRoles().stream()
                                        .filter(role -> !role.startsWith("dc-")) // Pekko's data center role
                                        .collect(Collectors.toCollection(TreeSet::new)),
                                !state.getUnreachable().contains(member)))
                        .toList());
    }
}

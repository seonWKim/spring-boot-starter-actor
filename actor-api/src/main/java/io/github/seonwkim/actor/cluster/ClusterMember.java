package io.github.seonwkim.actor.cluster;

import java.util.Set;

/**
 * A node of the cluster at the time of an event.
 *
 * @param address the node's address, e.g. {@code pekko://app@10.0.0.1:2551}
 * @param roles the node's roles
 * @param status the node's status
 */
public record ClusterMember(String address, Set<String> roles, MemberStatus status) {
    public ClusterMember {
        roles = Set.copyOf(roles);
    }
}

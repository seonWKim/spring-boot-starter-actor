package io.github.seonwkim.actor.cluster;

import java.util.Optional;

/**
 * A change in cluster membership, reachability or leadership. The starter publishes each one as a
 * Spring application event, so a bean receives it with {@code @EventListener}:
 *
 * <pre>{@code
 * @EventListener
 * void on(ClusterEvent.MemberUp event) { ... }
 * }</pre>
 *
 * <p>Events other than these (for example role leader changes) are not published; subscribe to
 * them on the engine's cluster through {@code SpringActorSystem.unwrap}.
 */
public sealed interface ClusterEvent {

    /** A member asked to join. */
    record MemberJoined(ClusterMember member) implements ClusterEvent {}

    /** A member is up while some members are unreachable. */
    record MemberWeaklyUp(ClusterMember member) implements ClusterEvent {}

    /** A member is up. */
    record MemberUp(ClusterMember member) implements ClusterEvent {}

    /** A member started leaving. */
    record MemberLeft(ClusterMember member) implements ClusterEvent {}

    /** A member finished handing over and is exiting. */
    record MemberExited(ClusterMember member) implements ClusterEvent {}

    /** A member was marked down. */
    record MemberDowned(ClusterMember member) implements ClusterEvent {}

    /**
     * A member was removed. {@code previousStatus} tells a graceful leave ({@link
     * MemberStatus#EXITING}) from a downed node ({@link MemberStatus#DOWN}).
     */
    record MemberRemoved(ClusterMember member, MemberStatus previousStatus) implements ClusterEvent {}

    /** A member stopped answering failure detection. */
    record UnreachableMember(ClusterMember member) implements ClusterEvent {}

    /** An unreachable member answers again. */
    record ReachableMember(ClusterMember member) implements ClusterEvent {}

    /** The leader changed; empty when there is none. */
    record LeaderChanged(Optional<String> leaderAddress) implements ClusterEvent {}
}

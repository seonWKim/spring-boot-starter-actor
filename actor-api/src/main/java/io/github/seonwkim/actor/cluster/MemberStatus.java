package io.github.seonwkim.actor.cluster;

/** Where a member is in its life in the cluster. */
public enum MemberStatus {
    JOINING,
    WEAKLY_UP,
    UP,
    LEAVING,
    EXITING,
    DOWN,
    REMOVED,
    PREPARING_FOR_SHUTDOWN,
    READY_FOR_SHUTDOWN
}

package io.github.seonwkim.actor.internal.cluster;

import io.github.seonwkim.actor.cluster.ClusterEvent;
import io.github.seonwkim.actor.cluster.ClusterMember;
import io.github.seonwkim.actor.cluster.MemberStatus;
import java.util.Optional;
import javax.annotation.Nullable;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.cluster.ClusterEvent.ClusterDomainEvent;
import org.apache.pekko.cluster.Member;
import org.springframework.context.ApplicationEventPublisher;

/** Turns Pekko cluster events into {@link ClusterEvent} records and publishes them to Spring. */
public final class PekkoClusterEvents {

    private PekkoClusterEvents() {}

    public static Behavior<ClusterDomainEvent> publisher(ApplicationEventPublisher publisher) {
        return Behaviors.receive(ClusterDomainEvent.class)
                .onMessage(ClusterDomainEvent.class, event -> {
                    ClusterEvent mapped = toStarter(event);
                    if (mapped != null) {
                        publisher.publishEvent(mapped);
                    }
                    return Behaviors.same();
                })
                .build();
    }

    /** The starter's record for a Pekko event, or null for an event the starter doesn't publish. */
    @Nullable static ClusterEvent toStarter(ClusterDomainEvent event) {
        if (event instanceof org.apache.pekko.cluster.ClusterEvent.MemberJoined e) {
            return new ClusterEvent.MemberJoined(member(e.member()));
        }
        if (event instanceof org.apache.pekko.cluster.ClusterEvent.MemberWeaklyUp e) {
            return new ClusterEvent.MemberWeaklyUp(member(e.member()));
        }
        if (event instanceof org.apache.pekko.cluster.ClusterEvent.MemberUp e) {
            return new ClusterEvent.MemberUp(member(e.member()));
        }
        if (event instanceof org.apache.pekko.cluster.ClusterEvent.MemberLeft e) {
            return new ClusterEvent.MemberLeft(member(e.member()));
        }
        if (event instanceof org.apache.pekko.cluster.ClusterEvent.MemberExited e) {
            return new ClusterEvent.MemberExited(member(e.member()));
        }
        if (event instanceof org.apache.pekko.cluster.ClusterEvent.MemberDowned e) {
            return new ClusterEvent.MemberDowned(member(e.member()));
        }
        if (event instanceof org.apache.pekko.cluster.ClusterEvent.MemberRemoved e) {
            return new ClusterEvent.MemberRemoved(member(e.member()), status(e.previousStatus()));
        }
        if (event instanceof org.apache.pekko.cluster.ClusterEvent.UnreachableMember e) {
            return new ClusterEvent.UnreachableMember(member(e.member()));
        }
        if (event instanceof org.apache.pekko.cluster.ClusterEvent.ReachableMember e) {
            return new ClusterEvent.ReachableMember(member(e.member()));
        }
        if (event instanceof org.apache.pekko.cluster.ClusterEvent.LeaderChanged e) {
            return new ClusterEvent.LeaderChanged(
                    Optional.ofNullable(e.getLeader()).map(Object::toString));
        }
        return null;
    }

    static ClusterMember member(Member member) {
        return new ClusterMember(member.address().toString(), member.getRoles(), status(member.status()));
    }

    static MemberStatus status(org.apache.pekko.cluster.MemberStatus status) {
        if (status == org.apache.pekko.cluster.MemberStatus.joining()) return MemberStatus.JOINING;
        if (status == org.apache.pekko.cluster.MemberStatus.weaklyUp()) return MemberStatus.WEAKLY_UP;
        if (status == org.apache.pekko.cluster.MemberStatus.up()) return MemberStatus.UP;
        if (status == org.apache.pekko.cluster.MemberStatus.leaving()) return MemberStatus.LEAVING;
        if (status == org.apache.pekko.cluster.MemberStatus.exiting()) return MemberStatus.EXITING;
        if (status == org.apache.pekko.cluster.MemberStatus.down()) return MemberStatus.DOWN;
        if (status == org.apache.pekko.cluster.MemberStatus.removed()) return MemberStatus.REMOVED;
        if (status == org.apache.pekko.cluster.MemberStatus.shuttingDown()) return MemberStatus.PREPARING_FOR_SHUTDOWN;
        if (status == org.apache.pekko.cluster.MemberStatus.shutDown()) return MemberStatus.READY_FOR_SHUTDOWN;
        throw new IllegalArgumentException("Unknown member status: " + status);
    }
}

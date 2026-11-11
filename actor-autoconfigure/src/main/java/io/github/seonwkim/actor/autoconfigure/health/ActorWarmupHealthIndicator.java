package io.github.seonwkim.actor.autoconfigure.health;

import io.github.seonwkim.actor.SpringActorSystem;
import io.github.seonwkim.actor.cluster.MemberStatus;
import io.github.seonwkim.actor.internal.cluster.ClusterStateView;
import io.github.seonwkim.actor.spi.PeerRegistry;
import java.time.Duration;
import javax.annotation.Nullable;
import org.springframework.boot.actuate.health.AbstractHealthIndicator;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

/**
 * Whether this node is warmed up: it is an Up member, the cluster it sees has at least
 * {@code minMembers} Up members, and its discovery store answers (when it finds peers through a
 * {@link PeerRegistry}). {@code UP} when all three hold, {@code OUT_OF_SERVICE} otherwise.
 *
 * <p>It says nothing about traffic: a blue-green pipeline waits for it on the standby color before it
 * activates that color, while the traffic readiness group may still refuse requests. Keep it out of
 * liveness, like {@code actorCluster}.
 */
public class ActorWarmupHealthIndicator extends AbstractHealthIndicator {

    private final SpringActorSystem actorSystem;
    private final int minMembers;

    @Nullable private final PeerRegistry peerRegistry;

    private final Duration discoveryTtl;

    public ActorWarmupHealthIndicator(
            SpringActorSystem actorSystem, int minMembers, @Nullable PeerRegistry peerRegistry, Duration discoveryTtl) {
        super("Actor warm-up health check failed");
        if (minMembers < 1) {
            throw new IllegalArgumentException(
                    ActorHealthConfiguration.WARMUP_MIN_MEMBERS + " must be at least 1, was " + minMembers);
        }
        this.actorSystem = actorSystem;
        this.minMembers = minMembers;
        this.peerRegistry = peerRegistry;
        this.discoveryTtl = discoveryTtl;
    }

    @Override
    protected void doHealthCheck(Health.Builder builder) {
        ClusterStateView view = ClusterStateView.of(actorSystem);
        String discovery = discovery();
        builder.status(statusOf(view, minMembers, !"UNREACHABLE".equals(discovery)))
                .withDetail("selfUp", selfUp(view))
                .withDetail("upMembers", upMembers(view))
                .withDetail("minMembers", minMembers)
                .withDetail("discovery", discovery);
    }

    static Status statusOf(ClusterStateView view, int minMembers, boolean discoveryReachable) {
        return selfUp(view) && upMembers(view) >= minMembers && discoveryReachable ? Status.UP : Status.OUT_OF_SERVICE;
    }

    private static boolean selfUp(ClusterStateView view) {
        return !view.terminated() && !view.selfUnreachable() && view.selfStatus() == MemberStatus.UP;
    }

    private static long upMembers(ClusterStateView view) {
        return view.nodes().stream()
                .filter(node -> node.status() == MemberStatus.UP)
                .count();
    }

    /** REACHABLE or UNREACHABLE for a peer registry, NOT_USED without one. */
    private String discovery() {
        if (peerRegistry == null) {
            return "NOT_USED";
        }
        try {
            // Any read proves the store answers; the entries themselves don't matter here.
            peerRegistry.peers(actorSystem.getName(), discoveryTtl);
            return "REACHABLE";
        } catch (RuntimeException e) {
            return "UNREACHABLE";
        }
    }
}
